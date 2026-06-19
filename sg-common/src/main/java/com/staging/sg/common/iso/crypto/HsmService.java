package com.staging.sg.common.iso.crypto;

/**
 * Interface HSM pour DMAS (KEK/PEK/MAK).
 *
 * Abstraction au-dessus du moteur crypto : l'impl actuelle (JposHsmService)
 * utilise jPOS JCESecurityModule pour la vraie crypto 3DES sous LMK, et
 * construit en parallèle les objets commande Thales (A0, A6...) prêts pour
 * une future migration vers un vrai HSM.
 *
 * Convention DMAS :
 *   KEK = ZMK (Key Encryption Key)  — racine, en key_store par member_group
 *   PEK = ZPK (PIN Encryption Key)  — chiffre le PIN block (DE052)
 *   MAK = ZAK (MAC key)             — calcule/vérifie le MAC (DE064)
 */
public interface HsmService {

    /** Résultat d'une opération sur une clé de travail. */
    class KeyResult {
        public byte[] clearKey;
        public String clearKeyHex;
        public byte[] keyUnderKek;
        public String keyUnderKekHex;
        public String keyUnderLmkHex;
        public String kcv;
        public String thalesCommand;
    }

    KeyResult generateWorkingKey(String keyType, int keyLengthBytes, String kekClearHex) throws Exception;

    KeyResult importWorkingKey(String keyType, String keyUnderKekHex, String kekClearHex, int keyLengthBytes) throws Exception;

    String computeKcv(byte[] clearKey) throws Exception;

    byte[] encryptPinBlock(byte[] pinBlock, byte[] pekClear) throws Exception;

    byte[] decryptPinBlock(byte[] encryptedPinBlock, byte[] pekClear) throws Exception;

    byte[] generateMac(byte[] data, byte[] makClear) throws Exception;

    boolean verifyMac(byte[] data, byte[] makClear, byte[] expectedMac) throws Exception;
}
