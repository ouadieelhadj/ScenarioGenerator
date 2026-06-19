package com.staging.sg.common.iso.crypto;

import org.jpos.iso.ISOUtil;
import org.jpos.security.SMAdapter;
import org.jpos.security.SecureDESKey;
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

    @Override
    public byte[] encryptPinBlock(byte[] pinBlock, byte[] pekClear) throws Exception {
        // placeholder — sera implémenté à l'étape PIN (DE052)
        throw new UnsupportedOperationException("encryptPinBlock: à implémenter étape PIN");
    }

    @Override
    public byte[] decryptPinBlock(byte[] encryptedPinBlock, byte[] pekClear) throws Exception {
        throw new UnsupportedOperationException("decryptPinBlock: à implémenter étape PIN");
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
}
