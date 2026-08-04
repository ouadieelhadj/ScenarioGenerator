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
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
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
        return (short) (keyLengthBytes >= 24 ? 192 : keyLengthBytes >= 16 ? 128 : 64);
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
            case "TPK" -> SMAdapter.TYPE_TPK;
            case "MAK" -> SMAdapter.TYPE_ZAK;
            case "PVK" -> SMAdapter.TYPE_PVK;
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

    /**
     * Generates one Way4/F20 working key, keeps it under the local LMK and
     * wraps the same key in an ANSI X9.143 TR-31 version-D block.
     */
    public Tr31KeyResult generateTr31WorkingKey(
            String keyType, int keyLengthBytes, String kbpkClearHex,
            String keyVersion) throws Exception {
        if (!("TAK".equals(keyType) || "TPK".equals(keyType))) {
            throw new IllegalArgumentException("TR-31 RKI supports TAK/TPK only");
        }
        if (keyLengthBytes != 16) {
            throw new IllegalArgumentException(
                    "Way4 F20 TAK/TPK must contain 16 bytes");
        }
        byte[] kbpk = ISOUtil.hex2byte(kbpkClearHex);
        byte[] clear = null;
        try {
            SecureDESKey workUnderLmk = sm.generateKey(
                    jposLen(keyLengthBytes), smType(keyType));
            byte[] kcvBytes = sm.generateKeyCheckValue(workUnderLmk);
            String kcv = ISOUtil.hexString(kcvBytes)
                    .substring(0, 6).toUpperCase();
            String underLmk = ISOUtil.hexString(workUnderLmk.getKeyBytes())
                    .toUpperCase();
            clear = exposeClearKey(
                    keyType, underLmk, kcv, keyLengthBytes);
            String usage = "TAK".equals(keyType) ? "M3" : "P0";
            String block = Tr31VersionDKeyBlock.wrap(
                    kbpk, clear, usage, "N", keyVersion, "N");
            log.info(
                    "[HSM] generated Way4/F20 {} keyId={} KCV={} "
                            + "TR31=D0112 blockLength={}",
                    keyType, keyVersion, kcv, block.length());
            return new Tr31KeyResult(
                    underLmk, kcv,
                    block.getBytes(StandardCharsets.US_ASCII),
                    keyLengthBytes);
        } finally {
            Arrays.fill(kbpk, (byte) 0);
            if (clear != null) Arrays.fill(clear, (byte) 0);
        }
    }

    public record Tr31KeyResult(
            String keyUnderLmkHex, String kcv,
            byte[] keyBlockAscii, int keyLength) {
        public Tr31KeyResult {
            keyBlockAscii = keyBlockAscii.clone();
        }

        @Override
        public byte[] keyBlockAscii() {
            return keyBlockAscii.clone();
        }
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

    /**
     * Translates an ISO-0 PIN block from a terminal TPK to a destination
     * network PEK entirely inside the HSM boundary. No clear PIN is returned
     * to application code.
     */
    public byte[] translatePinBlock(
            byte[] pinBlockUnderTpk, String pan,
            String tpkUnderLmkHex, String tpkKcv, int tpkLength,
            String pekUnderLmkHex, String pekKcv, int pekLength) throws Exception {
        String pan12 = extractPan12(pan);
        SecureDESKey tpk = rebuildKey("TPK", tpkUnderLmkHex, tpkKcv, tpkLength);
        SecureDESKey pek = rebuildKey("PEK", pekUnderLmkHex, pekKcv, pekLength);
        EncryptedPIN underTpk =
                new EncryptedPIN(pinBlockUnderTpk, SMAdapter.FORMAT00, pan12, true);
        EncryptedPIN underLmk = sm.importPIN(underTpk, tpk);
        EncryptedPIN underPek = sm.exportPIN(underLmk, pek, SMAdapter.FORMAT00);
        byte[] translated = underPek.getPINBlock();
        log.info("[HSM] translatePinBlock ISO-0 pan=***{} source=TPK target=PEK blockLen={}",
                pan.length() >= 4 ? pan.substring(pan.length() - 4) : pan,
                translated.length);
        return translated;
    }

    /** Verifies a Visa PVV without returning the clear PIN to application code. */
    public boolean verifyPinPvv(
            byte[] pinBlockUnderTpk, String pan,
            String tpkUnderLmkHex, String tpkKcv, int tpkLength,
            String pvkAUnderLmkHex, String pvkAKcv,
            String pvkBUnderLmkHex, String pvkBKcv,
            int pvki, String expectedPvv) throws Exception {
        String pan12 = extractPan12(pan);
        SecureDESKey tpk = rebuildKey("TPK", tpkUnderLmkHex, tpkKcv, tpkLength);
        SecureDESKey pvkA = rebuildKey("PVK", pvkAUnderLmkHex, pvkAKcv, 8);
        SecureDESKey pvkB = rebuildKey("PVK", pvkBUnderLmkHex, pvkBKcv, 8);
        EncryptedPIN underTpk =
                new EncryptedPIN(pinBlockUnderTpk, SMAdapter.FORMAT00, pan12, true);
        boolean verified = sm.verifyPVV(
                underTpk, tpk, pvkA, pvkB, pvki, expectedPvv);
        log.info("[HSM] verifyPinPvv pan=***{} verified={}",
                pan.length() >= 4 ? pan.substring(pan.length() - 4) : pan,
                verified);
        return verified;
    }

    /** Test/provisioning helper: creates an ISO-0 block under a terminal TPK. */
    public byte[] encryptPinBlockUnderTpk(
            String pin, String pan, String tpkUnderLmkHex,
            String kcv, int keyLenBytes) throws Exception {
        SecureDESKey tpk = rebuildKey("TPK", tpkUnderLmkHex, kcv, keyLenBytes);
        String pan12 = extractPan12(pan);
        EncryptedPIN underLmk = sm.encryptPIN(pin, pan12, true);
        return sm.exportPIN(underLmk, tpk, SMAdapter.FORMAT00).getPINBlock();
    }

    /** Calculates a Visa PVV inside the HSM boundary for controlled provisioning. */
    public String calculatePinPvv(
            byte[] pinBlockUnderTpk, String pan,
            String tpkUnderLmkHex, String tpkKcv, int tpkLength,
            String pvkAUnderLmkHex, String pvkAKcv,
            String pvkBUnderLmkHex, String pvkBKcv,
            int pvki) throws Exception {
        String pan12 = extractPan12(pan);
        SecureDESKey tpk = rebuildKey("TPK", tpkUnderLmkHex, tpkKcv, tpkLength);
        SecureDESKey pvkA = rebuildKey("PVK", pvkAUnderLmkHex, pvkAKcv, 8);
        SecureDESKey pvkB = rebuildKey("PVK", pvkBUnderLmkHex, pvkBKcv, 8);
        EncryptedPIN underTpk =
                new EncryptedPIN(pinBlockUnderTpk, SMAdapter.FORMAT00, pan12, true);
        return sm.calculatePVV(underTpk, tpk, pvkA, pvkB, pvki);
    }

    /** Reconstruit un SecureDESKey (sous LMK local) depuis hex + KCV. */
    private SecureDESKey rebuildKey(String keyType, String underLmkHex, String kcv, int keyLenBytes) {
        short len = jposLen(keyLenBytes);
        return new SecureDESKey(len, smType(keyType), underLmkHex, kcv);
    }

    /**
     * Validates key metadata inside the HSM boundary without exposing the
     * clear key. The supplied value must already be encrypted under this
     * server's LMK.
     */
    public boolean validateKeyUnderLmk(
            String keyType, String underLmkHex, String expectedKcv,
            int keyLenBytes) throws Exception {
        SecureDESKey key = rebuildKey(
                keyType, underLmkHex, expectedKcv, keyLenBytes);
        byte[] generated = sm.generateKeyCheckValue(key);
        String actualKcv = ISOUtil.hexString(generated)
                .substring(0, 6).toUpperCase();
        byte[] actual = ISOUtil.hex2byte(actualKcv);
        byte[] expected = ISOUtil.hex2byte(expectedKcv.toUpperCase());
        return java.security.MessageDigest.isEqual(actual, expected);
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
    //  - cle     : TAK/ZMK double longueur (scheme U, key type 003)
    //  - algo    : ISO 9797-1 Algorithme 1 en 3DES-CBC (M6 algorithm 01)
    //  - padding : ISO 9797 Padding Method 1 = zeros
    //  - donnee  : DEs bruts construits par SwamMacBuilder
    //  - DE128   : les 4 PREMIERS octets du MAC
    // ========================================================================

    /**
     * Reproduit la commande M6 :
     * {@code M6|0|0|01|1|003|U<key>|<length>|<buffer>}.
     *
     * La cle double longueur K1||K2 est developpee en K1||K2||K1, puis le
     * buffer est chiffre en 3DES-CBC avec IV nul et padding zero. Le MAC est
     * le dernier bloc chiffre.
     *
     * Retourne 8 octets. L'appelant tronque a swam.mac.length (4) pour DE128.
     */
    public byte[] generateMacZmk(byte[] data, String zmkClearHex) throws Exception {
        byte[] zmk = ISOUtil.hex2byte(zmkClearHex);
        final byte[] key24;
        if (zmk.length == 16) {
            key24 = concat(zmk, java.util.Arrays.copyOfRange(zmk, 0, 8));
        } else if (zmk.length == 24) {
            key24 = zmk;
        } else if (zmk.length == 8) {
            key24 = concat(concat(zmk, zmk), zmk);
        } else {
            throw new IllegalArgumentException("ZMK attendue 8/16/24 octets, recue " + zmk.length);
        }

        byte[] padded = zeroPad(data);
        Cipher cipher = Cipher.getInstance("DESede/CBC/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE,
                new SecretKeySpec(key24, "DESede"),
                new javax.crypto.spec.IvParameterSpec(new byte[8]));
        byte[] encrypted = cipher.doFinal(padded);
        byte[] mac = java.util.Arrays.copyOfRange(
                encrypted, encrypted.length - 8, encrypted.length);

        log.info("[HSM] generateMacZmk (M6 Alg01 / 3DES-CBC, cle {}o) dataLen={} padded={} mac8={} mac4={}",
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

    /**
     * Rend les octets EN CLAIR d'une cle stockee sous LMK.
     *
     * getKeyBytes() sur un SecureDESKey retourne la cle chiffree sous
     * LMK, pas le clair — l'utiliser pour deriver donnait des resultats
     * differents entre modules (LMK distinctes). On dechiffre donc
     * reellement sous LMK via jceHandler.decryptData, comme le HSM le
     * fait en interne pour ses propres operations.
     *
     * Le clair n'existe qu'en memoire, le temps du calcul EMV ; il n'est
     * jamais ecrit en base.
     */
    public byte[] exposeClearKey(String keyType, String underLmkHex, String kcv, int keyLenBytes) throws Exception {
        SecureDESKey k = rebuildKey(keyType, underLmkHex, kcv, keyLenBytes);
        // decryptFromLMK est protected dans JCESecurityModule : reflection.
        java.lang.reflect.Method m = null;
        Class<?> c = sm.getClass();
        while (c != null && m == null) {
            try {
                m = c.getDeclaredMethod("decryptFromLMK", SecureDESKey.class);
            } catch (NoSuchMethodException ignore) {
                c = c.getSuperclass();
            }
        }
        if (m == null) {
            throw new IllegalStateException("decryptFromLMK introuvable sur " + sm.getClass());
        }
        m.setAccessible(true);
        Object key = m.invoke(sm, k);   // javax.crypto.spec.SecretKeySpec ou Key
        byte[] clear;
        if (key instanceof java.security.Key jk) {
            clear = jk.getEncoded();
        } else if (key instanceof byte[] b) {
            clear = b;
        } else {
            throw new IllegalStateException("Type inattendu de decryptFromLMK : " + key.getClass());
        }
        // Un DESede sur 16 octets peut revenir sur 24 (K1K2K3=K1K2K1) : on retaille
        if (keyLenBytes == 16 && clear.length == 24) {
            byte[] t = new byte[16];
            System.arraycopy(clear, 0, t, 0, 16);
            clear = t;
        }
        return clear;
    }

    /**
     * Computes OpenWay POS DE64 while keeping LMK handling inside the HSM
     * boundary. A production Thales adapter can replace this method without
     * exposing the TAK to callers.
     */
    public byte[] generateWayPosMac(
            byte[] data, String takUnderLmkHex, String kcv, int keyLenBytes,
            WayPosMac.DataMode mode) throws Exception {
        byte[] clear = exposeClearKey("TAK", takUnderLmkHex, kcv, keyLenBytes);
        try {
            return WayPosMac.calculate(clear, data, mode);
        } finally {
            java.util.Arrays.fill(clear, (byte) 0);
        }
    }
}
