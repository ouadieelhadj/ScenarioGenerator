package com.staging.sg.common.iso.crypto;

import org.jpos.iso.ISOUtil;
import org.jpos.security.SMAdapter;
import org.jpos.security.SecureDESKey;
import org.jpos.security.EncryptedPIN;
import org.jpos.security.jceadapter.JCESecurityModule;
import org.jpos.core.SimpleConfiguration;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import java.security.Security;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.File;
import java.util.Properties;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

/**
 * Implémentation HSM via jPOS JCESecurityModule (crypto 3DES réelle sous LMK).
 * Construit en parallèle les objets commande Thales (A0/A6...) pour traçabilité
 * et préparation d'une future migration vers un vrai HSM Thales.
 *
 * Un bean par module, chacun avec son propre fichier LMK (dmas.lmk.file).
 * Au boot : si le fichier LMK n'existe pas, il est créé (rebuildlmk=true).
 */
@Service
public class JposHsmService implements HsmService {

    private static final Logger log = LoggerFactory.getLogger(JposHsmService.class);

    // Codes type clé Thales (host command)
    private static final String THALES_ZMK = "000";
    private static final String THALES_ZPK = "001";
    private static final String THALES_ZAK = "008";

    @Value("${dmas.lmk.file:D:/MoneyCore/ScenarioGenerator/keys/dmas-lmk.lmk}")
    private String lmkFile;

    /**
     * DEBUG UNIQUEMENT — affiche le PIN EN CLAIR dans les logs.
     * DOIT rester false en production. Activation explicite au lancement :
     *     -Dswam.debug.pin-clear=true
     */
    @Value("${swam.debug.pin-clear:false}")
    private boolean debugPinClear;

    @Value("${dmas.lmk.rebuild:false}")
    private boolean lmkRebuild;

    private JCESecurityModule sm;

    @PostConstruct
    public void init() {
        try {
            File f = new File(lmkFile);
            boolean needRebuild = lmkRebuild || !f.exists();
            if (f.getParentFile() != null) f.getParentFile().mkdirs();

            if (Security.getProvider("BC") == null) {
                Security.addProvider(new BouncyCastleProvider());
            }
            Properties p = new Properties();
            p.setProperty("lmk", lmkFile);
            p.setProperty("rebuildlmk", String.valueOf(needRebuild));
            p.setProperty("provider", "org.bouncycastle.jce.provider.BouncyCastleProvider");
            SimpleConfiguration cfg = new SimpleConfiguration(p);

            sm = new JCESecurityModule();
            sm.setConfiguration(cfg);

            log.info("[HSM] JCESecurityModule prêt — lmk={} rebuild={}", lmkFile, needRebuild);
        } catch (Exception e) {
            log.error("[HSM] Échec init JCESecurityModule : {}", e.getMessage(), e);
            throw new RuntimeException("HSM init failed", e);
        }
    }

    // keyLength jPOS : 128 = double (16o), 192 = triple (24o)
    private short jposLen(int keyLengthBytes) {
        return (short) (keyLengthBytes >= 24 ? 192 : 128);
    }

    private String thalesType(String keyType) {
        return switch (keyType) {
            case "PEK" -> THALES_ZPK;
            case "MAK" -> THALES_ZAK;
            default    -> THALES_ZMK;
        };
    }

    private String smType(String keyType) {
        return switch (keyType) {
            case "PEK" -> SMAdapter.TYPE_ZPK;
            case "MAK" -> SMAdapter.TYPE_ZAK;
            default    -> SMAdapter.TYPE_ZMK;
        };
    }

    /** Forme un SecureDESKey (sous LMK local) depuis une valeur claire hex. */
    public SecureDESKey formClearKey(String keyType, String clearHex) throws Exception {
        short len = jposLen(clearHex.length() / 2);
        return sm.formKEYfromClearComponents(len, smType(keyType), clearHex);
    }

    /** Résultat du formage d'un KEK sous LMK local. */
    public static class KekUnderLmk {
        public String underLmkHex;   // SecureDESKey (KEK) chiffré sous LMK local, en hex
        public String kcv;           // KCV du KEK
    }

    /**
     * Forme le KEK (valeur claire hex) sous le LMK local et retourne
     * sa représentation chiffrée sous LMK + son KCV.
     * Utilisé par le bootstrap KEK (un appel par module).
     */
    public KekUnderLmk formKekUnderLmk(String kekClearHex) throws Exception {
        SecureDESKey kek = formClearKey("KEK", kekClearHex);
        KekUnderLmk r = new KekUnderLmk();
        r.underLmkHex = ISOUtil.hexString(kek.getKeyBytes()).toUpperCase();
        byte[] kcvBytes = sm.generateKeyCheckValue(kek);
        r.kcv = ISOUtil.hexString(kcvBytes).substring(0, 6).toUpperCase();
        return r;
    }

    @Override
    public KeyResult generateWorkingKey(String keyType, int keyLengthBytes, String kekClearHex) throws Exception {
        short len = jposLen(keyLengthBytes);

        // 1. Générer la clé de travail sous LMK local
        SecureDESKey workUnderLmk = sm.generateKey(len, smType(keyType));

        // 2. Former le KEK sous LMK local depuis sa valeur claire
        SecureDESKey kekUnderLmk = formClearKey("KEK", kekClearHex);

        // 3. Exporter la clé chiffrée sous KEK (pour transport réseau)
        byte[] underKek = sm.exportKey(workUnderLmk, kekUnderLmk);

        // 4. KCV
        byte[] kcvBytes = sm.generateKeyCheckValue(workUnderLmk);
        String kcv = ISOUtil.hexString(kcvBytes).substring(0, 6).toUpperCase();

        // 5. Commande Thales équivalente (A0 generate, mode 1 = export sous ZMK)
        ThalesCommand.A0 a0 = new ThalesCommand.A0(
                "0000", "1", thalesType(keyType), keyLengthBytes,
                ISOUtil.hexString(kekUnderLmk.getKeyBytes()));

        KeyResult r = new KeyResult();
        r.keyUnderKek = underKek;
        r.keyUnderKekHex = ISOUtil.hexString(underKek).toUpperCase();
        r.keyUnderLmkHex = ISOUtil.hexString(workUnderLmk.getKeyBytes()).toUpperCase();
        r.kcv = kcv;
        r.thalesCommand = a0.toWire();
        log.info("[HSM] generateWorkingKey {} — KCV={} underKEK={} thales={}",
                keyType, kcv, r.keyUnderKekHex, r.thalesCommand);
        return r;
    }

    @Override
    public KeyResult importWorkingKey(String keyType, String keyUnderKekHex, String kekClearHex, int keyLengthBytes) throws Exception {
        short len = jposLen(keyLengthBytes);
        byte[] underKek = ISOUtil.hex2byte(keyUnderKekHex);

        // 1. Former le KEK sous LMK local
        SecureDESKey kekUnderLmk = formClearKey("KEK", kekClearHex);

        // 2. Importer la clé (déchiffre sous KEK, re-chiffre sous LMK)
        SecureDESKey workUnderLmk = sm.importKey(len, smType(keyType), underKek, kekUnderLmk, false);

        // 3. KCV de la clé importée
        byte[] kcvBytes = sm.generateKeyCheckValue(workUnderLmk);
        String kcv = ISOUtil.hexString(kcvBytes).substring(0, 6).toUpperCase();

        // 4. Commande Thales équivalente (A6 import)
        ThalesCommand.A6 a6 = new ThalesCommand.A6(
                "0000", thalesType(keyType),
                ISOUtil.hexString(kekUnderLmk.getKeyBytes()),
                keyUnderKekHex, keyLengthBytes);

        KeyResult r = new KeyResult();
        r.keyUnderKekHex = keyUnderKekHex.toUpperCase();
        r.keyUnderLmkHex = ISOUtil.hexString(workUnderLmk.getKeyBytes()).toUpperCase();
        r.kcv = kcv;
        r.thalesCommand = a6.toWire();
        log.info("[HSM] importWorkingKey — KCV={} thales={}", kcv, r.thalesCommand);
        return r;
    }

    @Override
    public String computeKcv(byte[] clearKey) throws Exception {
        SecureDESKey k = formClearKey("KEK", ISOUtil.hexString(clearKey));
        byte[] kcv = sm.generateKeyCheckValue(k);
        return ISOUtil.hexString(kcv).substring(0, 6).toUpperCase();
    }

    /** Extrait les 12 chiffres ISO-0 : 12 droits hors check digit. */
    private static String extractPan12(String pan) {
        String digits = pan.replaceAll("[^0-9]", "");
        if (digits.length() < 13) {
            // pad à gauche pour atteindre au moins 13
            digits = String.format("%013d", Long.parseLong(digits.isEmpty() ? "0" : digits));
        }
        return digits.substring(digits.length() - 13, digits.length() - 1);
    }

    @Override
    public byte[] encryptPinBlock(String pin, String pan, String pekUnderLmkHex, String kcv, int keyLenBytes) throws Exception {
        SecureDESKey pek = rebuildKey("PEK", pekUnderLmkHex, kcv, keyLenBytes);
        // 1. PIN -> PIN block sous LMK (FORMAT00 = ISO-0)
        String pan12 = extractPan12(pan);
        EncryptedPIN underLmk = sm.encryptPIN(pin, pan12, true);
        // 2. Export sous PEK -> PIN block destiné au DE052
        EncryptedPIN underPek = sm.exportPIN(underLmk, pek, SMAdapter.FORMAT00);
        byte[] block = underPek.getPINBlock();
        if (debugPinClear) {
            log.warn("[HSM] *** DEBUG PIN EN CLAIR *** encryptPinBlock pan=***{} pin={} block={}  <<< A DESACTIVER EN PRODUCTION",
                    pan.length() >= 4 ? pan.substring(pan.length()-4) : pan,
                    pin, ISOUtil.hexString(block));
        } else {
            log.info("[HSM] encryptPinBlock — pan=***{} blockLen={} block={}",
                    pan.length() >= 4 ? pan.substring(pan.length()-4) : pan,
                    block.length, ISOUtil.hexString(block));
        }
        return block;
    }

    @Override
    public String decryptPinBlock(byte[] pinBlockUnderPek, String pan, String pekUnderLmkHex, String kcv, int keyLenBytes) throws Exception {
        SecureDESKey pek = rebuildKey("PEK", pekUnderLmkHex, kcv, keyLenBytes);
        // 1. Reconstruire l'EncryptedPIN reçu (sous PEK)
        String pan12 = extractPan12(pan);
        EncryptedPIN underPek = new EncryptedPIN(pinBlockUnderPek, SMAdapter.FORMAT00, pan12, true);
        // 2. Importer sous LMK puis déchiffrer
        EncryptedPIN underLmk = sm.importPIN(underPek, pek);
        String pin = sm.decryptPIN(underLmk);
        if (debugPinClear) {
            log.warn("[HSM] *** DEBUG PIN EN CLAIR *** decryptPinBlock pan=***{} pin={}  <<< A DESACTIVER EN PRODUCTION",
                    pan.length() >= 4 ? pan.substring(pan.length()-4) : pan, pin);
        } else {
            log.info("[HSM] decryptPinBlock — pan=***{} pinLen={}",
                    pan.length() >= 4 ? pan.substring(pan.length()-4) : pan, pin.length());
        }
        return pin;
    }

    /** Reconstruit un SecureDESKey (sous LMK local) depuis hex + KCV. */
    private SecureDESKey rebuildKey(String keyType, String underLmkHex, String kcv, int keyLenBytes) {
        short len = jposLen(keyLenBytes);
        return new SecureDESKey(len, smType(keyType), underLmkHex, kcv);
    }

    @Override
    public byte[] generateMac(byte[] data, String makUnderLmkHex, String kcv, int keyLenBytes) throws Exception {
        SecureDESKey mak = rebuildKey("MAK", makUnderLmkHex, kcv, keyLenBytes);
        byte[] mac = sm.generateCBC_MAC(data, mak);
        log.info("[HSM] generateMac — dataLen={} macLen={}", data.length, mac.length);
        return mac;
    }

    @Override
    public boolean verifyMac(byte[] data, String makUnderLmkHex, String kcv, int keyLenBytes, byte[] expectedMac) throws Exception {
        byte[] computed = generateMac(data, makUnderLmkHex, kcv, keyLenBytes);
        boolean ok = java.util.Arrays.equals(computed, expectedMac);
        log.info("[HSM] verifyMac — match={}", ok);
        return ok;
    }

    // ── SWAM : cles de travail SIMPLE longueur (DES 8o) ─────────────
    // La ZAK SWAM (tag P10=016) exige 8 octets = 16 hex sous KEK.
    // FIPS PUB 113 (MAC DE128) = DES-CBC-MAC a cle simple -> coherent.
    public KeyResult generateWorkingKeySingle(String keyType, String kekClearHex) throws Exception {
        short len = (short) 64; // LENGTH_DES simple
        SecureDESKey workUnderLmk = sm.generateKey(len, smType(keyType));
        SecureDESKey kekUnderLmk = formClearKey("KEK", kekClearHex);
        byte[] underKek = sm.exportKey(workUnderLmk, kekUnderLmk);
        byte[] kcvBytes = sm.generateKeyCheckValue(workUnderLmk);
        String kcv = ISOUtil.hexString(kcvBytes).substring(0, 6).toUpperCase();
        KeyResult r = new KeyResult();
        r.keyUnderKek = underKek;
        r.keyUnderKekHex = ISOUtil.hexString(underKek).toUpperCase();
        r.keyUnderLmkHex = ISOUtil.hexString(workUnderLmk.getKeyBytes()).toUpperCase();
        r.kcv = kcv;
        log.info("[HSM] generateWorkingKeySingle {} — KCV={} underKEK={} ({}hex)",
                keyType, kcv, r.keyUnderKekHex, r.keyUnderKekHex.length());
        return r;
    }

    public KeyResult importWorkingKeySingle(String keyType, String keyUnderKekHex, String kekClearHex) throws Exception {
        short len = (short) 64; // LENGTH_DES simple
        byte[] underKek = ISOUtil.hex2byte(keyUnderKekHex);
        SecureDESKey kekUnderLmk = formClearKey("KEK", kekClearHex);
        SecureDESKey workUnderLmk = sm.importKey(len, smType(keyType), underKek, kekUnderLmk, false);
        byte[] kcvBytes = sm.generateKeyCheckValue(workUnderLmk);
        String kcv = ISOUtil.hexString(kcvBytes).substring(0, 6).toUpperCase();
        KeyResult r = new KeyResult();
        r.keyUnderKekHex = keyUnderKekHex.toUpperCase();
        r.keyUnderLmkHex = ISOUtil.hexString(workUnderLmk.getKeyBytes()).toUpperCase();
        r.kcv = kcv;
        log.info("[HSM] importWorkingKeySingle {} — KCV={}", keyType, kcv);
        return r;
    }

    // ── SWAM : DES-CBC-MAC "maison" (FIPS PUB 113 / ISO 9797-1 Alg 1) ──
    // jPOS generateCBC_MAC() utilise ISO9797ALG3 (Retail MAC) qui EXIGE une cle
    // 16 octets ; notre ZAK SWAM fait 8 octets (tag P10=016). On calcule donc
    // le MAC nous-memes : DES-CBC (IV=0, padding zero), MAC = dernier bloc (8o).
    // Utilise UNIQUEMENT par SWAM. DMAS continue d'utiliser generateMac/verifyMac.

    /** Dechiffre la cle de travail (sous KEK) pour obtenir sa valeur claire. */
    private byte[] decryptKeyUnderKek(String keyUnderKekHex, String kekClearHex) throws Exception {
        byte[] kek = ISOUtil.hex2byte(kekClearHex);
        byte[] enc = ISOUtil.hex2byte(keyUnderKekHex);
        byte[] kek24 = (kek.length == 16)
                ? concat(kek, java.util.Arrays.copyOfRange(kek, 0, 8))  // 2-key -> K1K2K1
                : kek;
        Cipher c = Cipher.getInstance("DESede/ECB/NoPadding");
        c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(kek24, "DESede"));
        return c.doFinal(enc);
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] r = new byte[a.length + b.length];
        System.arraycopy(a, 0, r, 0, a.length);
        System.arraycopy(b, 0, r, a.length, b.length);
        return r;
    }

    /** Padding zero jusqu'a un multiple de 8 octets. */
    private static byte[] zeroPad(byte[] data) {
        int rem = data.length % 8;
        if (rem == 0 && data.length > 0) return data;
        int newLen = data.length + (8 - rem);
        byte[] out = new byte[newLen];
        System.arraycopy(data, 0, out, 0, data.length);
        return out;
    }

    /**
     * DES-CBC-MAC (FIPS 113) avec ZAK simple longueur (8 octets).
     * @param keyUnderKekHex ZAK chiffree sous KEK (16 hex)
     * @param kekClearHex    KEK claire (32 hex si double longueur)
     * @return MAC 8 octets (dernier bloc CBC)
     */
    public byte[] generateMacSingle(byte[] data, String keyUnderKekHex, String kekClearHex) throws Exception {
        byte[] zak = decryptKeyUnderKek(keyUnderKekHex, kekClearHex);
        if (zak.length != 8) {
            throw new IllegalArgumentException("ZAK attendue 8 octets, recue " + zak.length);
        }
        byte[] padded = zeroPad(data);
        Cipher c = Cipher.getInstance("DES/CBC/NoPadding");
        c.init(Cipher.ENCRYPT_MODE,
               new SecretKeySpec(zak, "DES"),
               new javax.crypto.spec.IvParameterSpec(new byte[8]));
        byte[] all = c.doFinal(padded);
        byte[] mac = java.util.Arrays.copyOfRange(all, all.length - 8, all.length);
        log.info("[HSM] generateMacSingle (DES-CBC-MAC FIPS113) dataLen={} padded={} mac={}",
                data.length, padded.length, ISOUtil.hexString(mac));
        return mac;
    }

    public boolean verifyMacSingle(byte[] data, String keyUnderKekHex, String kekClearHex, byte[] expectedMac) throws Exception {
        byte[] computed = generateMacSingle(data, keyUnderKekHex, kekClearHex);
        boolean ok = java.util.Arrays.equals(computed, expectedMac);
        log.info("[HSM] verifyMacSingle — attendu={} calcule={} match={}",
                ISOUtil.hexString(expectedMac), ISOUtil.hexString(computed), ok);
        return ok;
    }
    // ========================================================================
    //  MAC SWAM REEL — VALIDE PAR WAY4 (RC[0]) le 14/07/2026
    //  - cle     : la ZMK EN CLAIR, utilisee comme ZAK (16 octets)
    //  - algo    : ANSI X9.19 = ISO 9797-1 Algorithme 3 (retail MAC)
    //  - padding : ISO 9797 Padding Method 1 = zeros (zeroPad)
    //  - donnee  : MTI + bitmap (bit128 ON) + DEs, sans la valeur du MAC
    //  - DE128   : les 4 PREMIERS octets du MAC
    // ========================================================================

    /**
     * MAC ANSI X9.19 (ISO 9797-1 Algorithme 3, "retail MAC"), padding Method 1 (zeros).
     *
     * VALIDE PAR LE MEMBRE REEL WAY4 le 14/07/2026 : Verify MAC Rs RC[0] VerifRC[0].
     *
     * Conforme aux logs Way4 :
     *   macAlgorithm      = ANSI_X9_19
     *   bufferPaddingMode = ISO_9797_PADDING_METHOD_1
     *   key               = ZAK (= la ZMK cote SWAM, 16 octets)
     *
     * Cle 16 octets (double longueur K1||K2) :
     *   - CBC-DES avec K1 sur TOUS les blocs
     *   - sur le DERNIER bloc uniquement : DES-decrypt K2 puis DES-encrypt K1
     * Cle 8 octets : X9.19 degenere en DES-CBC-MAC simple (K1==K2).
     *
     * Retourne 8 octets. L'appelant tronque a swam.mac.length (4) pour DE128.
     */
    public byte[] generateMacZmk(byte[] data, String zmkClearHex) throws Exception {
        byte[] zmk = ISOUtil.hex2byte(zmkClearHex);
        byte[] k1, k2;
        if (zmk.length == 16 || zmk.length == 24) {
            k1 = java.util.Arrays.copyOfRange(zmk, 0, 8);
            k2 = java.util.Arrays.copyOfRange(zmk, 8, 16);
        } else if (zmk.length == 8) {
            k1 = zmk; k2 = zmk;
        } else {
            throw new IllegalArgumentException("ZMK attendue 8/16/24 octets, recue " + zmk.length);
        }

        byte[] padded = zeroPad(data);

        Cipher desK1 = Cipher.getInstance("DES/CBC/NoPadding");
        desK1.init(Cipher.ENCRYPT_MODE,
                   new SecretKeySpec(k1, "DES"),
                   new javax.crypto.spec.IvParameterSpec(new byte[8]));
        byte[] all  = desK1.doFinal(padded);
        byte[] last = java.util.Arrays.copyOfRange(all, all.length - 8, all.length);

        Cipher decK2 = Cipher.getInstance("DES/ECB/NoPadding");
        decK2.init(Cipher.DECRYPT_MODE, new SecretKeySpec(k2, "DES"));
        byte[] tmp = decK2.doFinal(last);

        Cipher encK1 = Cipher.getInstance("DES/ECB/NoPadding");
        encK1.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(k1, "DES"));
        byte[] mac = encK1.doFinal(tmp);

        log.info("[HSM] generateMacZmk (X9.19 / ISO9797-3, cle {}o) dataLen={} padded={} mac8={} mac4={}",
                zmk.length, data.length, padded.length,
                ISOUtil.hexString(mac),
                ISOUtil.hexString(java.util.Arrays.copyOfRange(mac, 0, 4)));
        return mac;
    }

    /**
     * Verifie un MAC SWAM. Compare sur la longueur du MAC recu :
     *   - si expectedMac fait 4 octets -> on compare les 4 premiers du MAC calcule
     *   - si 8 octets -> comparaison complete
     */
    public boolean verifyMacZmk(byte[] data, String zmkClearHex, byte[] expectedMac) throws Exception {
        byte[] full = generateMacZmk(data, zmkClearHex);
        int n = (expectedMac == null) ? 0 : expectedMac.length;
        byte[] computed = (n > 0 && n < full.length)
                ? java.util.Arrays.copyOfRange(full, 0, n)
                : full;
        boolean ok = java.util.Arrays.equals(computed, expectedMac);
        log.info("[HSM] verifyMacZmk — attendu={} calcule={} match={}",
                ISOUtil.hexString(expectedMac), ISOUtil.hexString(computed), ok);
        return ok;
    }

    /**
     * Reconstruit une cle depuis sa valeur sous LMK et rend ses octets
     * EN CLAIR, uniquement en memoire, pour les calculs EMV (derivation
     * de la cle ICC puis de la cle de session). La cle n'est jamais
     * ecrite en clair en base.
     */
    public byte[] exposeClearKey(String keyType, String underLmkHex, String kcv, int keyLenBytes) throws Exception {
        SecureDESKey k = rebuildKey(keyType, underLmkHex, kcv, keyLenBytes);
        return k.getKeyBytes();
    }
}
