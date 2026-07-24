package com.staging.sg.mc.sms.issuer.network;

import com.staging.sg.common.iso.McSmsDe48;
import org.jpos.iso.ISOMsg;
import org.jpos.iso.ISOSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.DESedeKeySpec;
import java.security.SecureRandom;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Simulateur Mastercard — echange de cles, mecanisme 162.
 *
 * Reproduit le comportement observe dans une trace du simulateur officiel
 * (AcquirerSwitchSimulator, format Mastercard Credit 26Q3) :
 *
 *   1. recoit  0800 DE70=162  sollicitation du membre
 *   2. envoie  0810 DE70=162  DE39=00, sans cle
 *   3. genere une PEK aleatoire double longueur
 *   4. la chiffre sous ZMK en 3DES-ECB, calcule le KCV
 *   5. envoie  0800 DE70=161  avec DE48 subelement 11
 *   6. recoit  0810 DE70=161  accuse du membre
 *   7. envoie  0820 DE70=161  acquittement (DE48 sans cle)
 *
 * Le 0820 est emis meme si le membre a repondu en erreur — comportement
 * observe dans la trace (DE39=96 suivi malgre tout du 0820).
 *
 * CRYPTOGRAPHIE (verifiee contre la trace) :
 *   cle chiffree = 3DES-ECB(cle claire) sous ZMK
 *   KCV          = 3DES-ECB(8 octets nuls) avec la cle claire, tronque a 4
 *
 * Ce simulateur n'utilise PAS de HSM : c'est un outil de test, la
 * cryptographie est faite en JCE standard.
 */
@Service
public class McSmsIssKeyExchange {

    private static final Logger log = LoggerFactory.getLogger(McSmsIssKeyExchange.class);

    /** Delai avant la livraison de la cle, pour laisser passer le 0810. */
    private static final long DELAY_BEFORE_KEY_MS = 300;

    /** Delai avant l'acquittement 0820, apres le 0810 du membre. */
    private static final long DELAY_BEFORE_ACK_MS = 500;

    @Value("${mc.sms.iss.zmk:13AED5DA1F32347523C708C11F2608FD}")
    private String zmkHex;

    @Value("${mc.sms.iss.forwarding-id:002202}")
    private String forwardingId;

    private final AtomicInteger stanSeq = new AtomicInteger(1);

    /** Derniere cle livree, conservee pour le controle et les tests. */
    private volatile String lastClearKey;
    private volatile String lastEncryptedKey;
    private volatile String lastKcv;

    // ====================================================================
    //  ORCHESTRATION
    // ====================================================================

    /**
     * Lance la sequence de livraison apres avoir repondu au 0800/162.
     * Execute dans un thread separe pour ne pas bloquer le listener jPOS.
     */
    public void deliverKeyAsync(ISOSource source, ISOMsg solicitation) {
        Thread t = new Thread(() -> {
            try {
                Thread.sleep(DELAY_BEFORE_KEY_MS);
                deliverKey(source, solicitation);
            } catch (Exception e) {
                log.error("[MC-KEX-SIM] Echec de la livraison : {}", e.getMessage(), e);
            }
        }, "mc-sms-key-delivery");
        t.setDaemon(true);
        t.start();
    }

    /** Genere, chiffre et envoie la cle dans un 0800 DE70=161. */
    private void deliverKey(ISOSource source, ISOMsg solicitation) throws Exception {
        String clearKey = generateDoubleLengthKey();
        String encrypted = encryptUnderZmk(clearKey);
        String kcv = computeKcv(clearKey);

        lastClearKey = clearKey;
        lastEncryptedKey = encrypted;
        lastKcv = kcv;

        log.info("[MC-KEX-SIM] PEK generee    : {}", clearKey);
        log.info("[MC-KEX-SIM] Chiffree ZMK   : {}", encrypted);
        log.info("[MC-KEX-SIM] KCV            : {} (tronque : {})", kcv, kcv.substring(0, 4));

        McSmsDe48 de48 = new McSmsDe48()
                .putKeyExchange(McSmsDe48.KEY_CLASS_PIN, "00", "00", encrypted, kcv);

        ISOMsg m = new ISOMsg();
        m.setPackager(solicitation.getPackager());
        m.setMTI("0800");
        if (solicitation.hasField(2)) m.set(2, solicitation.getString(2));
        m.set(7,  utcDateTime());
        m.set(11, nextStan());
        m.set(33, forwardingId);
        m.set(48, de48.build());
        if (solicitation.hasField(63)) m.set(63, solicitation.getString(63));
        m.set(70, "161");

        source.send(m);
        log.info("[MC-KEX-SIM] Cle livree : 0800 DE70=161 STAN={} DE48={}",
                m.getString(11), de48.build());

        // Acquittement 0820 apres un court delai
        scheduleAcknowledgement(source, m);
    }

    /** Envoie le 0820 DE70=161 : la cle devient utilisable cote membre. */
    private void scheduleAcknowledgement(ISOSource source, ISOMsg delivery) {
        Thread t = new Thread(() -> {
            try {
                Thread.sleep(DELAY_BEFORE_ACK_MS);

                McSmsDe48 de48 = new McSmsDe48()
                        .putKeyExchangeAck(McSmsDe48.KEY_CLASS_PIN, "00", "00");

                ISOMsg m = new ISOMsg();
                m.setPackager(delivery.getPackager());
                m.setMTI("0820");
                if (delivery.hasField(2)) m.set(2, delivery.getString(2));
                m.set(7,  utcDateTime());
                m.set(11, delivery.getString(11));   // meme STAN que la livraison
                m.set(33, forwardingId);
                m.set(48, de48.build());
                if (delivery.hasField(63)) m.set(63, delivery.getString(63));
                m.set(70, "161");

                source.send(m);
                log.info("[MC-KEX-SIM] Acquittement envoye : 0820 DE70=161 STAN={}",
                        m.getString(11));

            } catch (Exception e) {
                log.error("[MC-KEX-SIM] Echec de l'acquittement : {}", e.getMessage(), e);
            }
        }, "mc-sms-key-ack");
        t.setDaemon(true);
        t.start();
    }

    // ====================================================================
    //  CRYPTOGRAPHIE
    // ====================================================================

    /** Genere une cle double longueur (16 octets) aleatoire. */
    private String generateDoubleLengthKey() {
        byte[] k = new byte[16];
        new SecureRandom().nextBytes(k);
        // Ajustement de parite impaire, usage DES
        for (int i = 0; i < k.length; i++) {
            int b = k[i] & 0xFE;
            int ones = Integer.bitCount(b);
            k[i] = (byte) (b | ((ones % 2 == 0) ? 1 : 0));
        }
        return hex(k);
    }

    /** Chiffre la cle claire sous la ZMK, 3DES-ECB sans padding. */
    private String encryptUnderZmk(String clearKeyHex) throws Exception {
        Cipher c = Cipher.getInstance("DESede/ECB/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, tripleDesKey(zmkHex));
        return hex(c.doFinal(unhex(clearKeyHex)));
    }

    /** KCV : chiffrement de 8 octets nuls avec la cle claire. */
    private String computeKcv(String clearKeyHex) throws Exception {
        Cipher c = Cipher.getInstance("DESede/ECB/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, tripleDesKey(clearKeyHex));
        return hex(c.doFinal(new byte[8]));
    }

    /**
     * Construit une cle 3DES. Une cle double longueur (16 octets) est
     * etendue en 24 octets selon la convention K1|K2|K1.
     */
    private SecretKey tripleDesKey(String hexKey) throws Exception {
        byte[] k = unhex(hexKey);
        if (k.length == 16) {
            byte[] k24 = new byte[24];
            System.arraycopy(k, 0, k24, 0, 16);
            System.arraycopy(k, 0, k24, 16, 8);
            k = k24;
        }
        return SecretKeyFactory.getInstance("DESede")
                .generateSecret(new DESedeKeySpec(k));
    }

    private static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder(b.length * 2);
        for (byte x : b) sb.append(String.format("%02X", x));
        return sb.toString();
    }

    private static byte[] unhex(String s) {
        int n = s.length() / 2;
        byte[] b = new byte[n];
        for (int i = 0; i < n; i++) {
            b[i] = (byte) Integer.parseInt(s.substring(i * 2, i * 2 + 2), 16);
        }
        return b;
    }

    // ====================================================================
    //  DIVERS
    // ====================================================================

    private String nextStan() {
        return String.format("%06d", 900000 + (stanSeq.getAndIncrement() % 100000));
    }

    private String utcDateTime() {
        return ZonedDateTime.now(ZoneOffset.UTC)
                .format(DateTimeFormatter.ofPattern("MMddHHmmss"));
    }

    public String getLastClearKey()     { return lastClearKey; }
    public String getLastEncryptedKey() { return lastEncryptedKey; }
    public String getLastKcv()          { return lastKcv; }
    public String getZmkHex()           { return zmkHex; }
}
