package com.staging.sg.common.iso.crypto;

/**
 * Objets commande Thales payShield (host commands).
 * Construits par JposHsmService en parallèle de la crypto jPOS réelle,
 * prêts à être envoyés à un vrai HSM lors d'une future migration.
 *
 * Format host (TCP/IP) : [Header][CommandCode][champs...]
 * Préfixe de schéma de clé : U = 3DES double (16o), T = 3DES triple (24o).
 */
public abstract class ThalesCommand {

    protected final String header;       // header applicatif (ex: "0000")
    protected final String commandCode;  // A0, A6, A8, BU...

    protected ThalesCommand(String header, String commandCode) {
        this.header = header;
        this.commandCode = commandCode;
    }

    /** Représentation "wire" de la commande (ce qu'on enverrait au HSM). */
    public abstract String toWire();

    public String getCommandCode() { return commandCode; }

    protected static String scheme(int keyLengthBytes) {
        return keyLengthBytes >= 24 ? "T" : "U";
    }

    // ── A0 : Generate Key ────────────────────────────────────
    /** A0 — génère une clé d'un type donné, optionnellement chiffrée sous ZMK. */
    public static class A0 extends ThalesCommand {
        public final String mode;        // "0" = sous LMK, "1" = sous LMK + sous ZMK
        public final String keyType;     // 000=ZMK, 001=ZPK, ... (code Thales 3 car)
        public final String keyScheme;   // U / T
        public final String zmkUnderLmk; // ZMK sous LMK (si export sous ZMK)

        public A0(String header, String mode, String keyType, int keyLengthBytes, String zmkUnderLmk) {
            super(header, "A0");
            this.mode = mode;
            this.keyType = keyType;
            this.keyScheme = scheme(keyLengthBytes);
            this.zmkUnderLmk = zmkUnderLmk;
        }

        @Override
        public String toWire() {
            StringBuilder sb = new StringBuilder();
            sb.append(header).append(commandCode)
              .append(mode).append(keyType).append(keyScheme);
            if ("1".equals(mode) && zmkUnderLmk != null) {
                sb.append(zmkUnderLmk).append(keyScheme);
            }
            return sb.toString();
        }
    }

    // ── A6 : Import Key ──────────────────────────────────────
    /** A6 — importe une clé chiffrée sous ZMK vers protection LMK. */
    public static class A6 extends ThalesCommand {
        public final String keyType;     // code Thales 3 car
        public final String zmkUnderLmk; // ZMK sous LMK
        public final String keyUnderZmk; // clé à importer, chiffrée sous ZMK
        public final String keyScheme;   // U / T

        public A6(String header, String keyType, String zmkUnderLmk,
                  String keyUnderZmk, int keyLengthBytes) {
            super(header, "A6");
            this.keyType = keyType;
            this.zmkUnderLmk = zmkUnderLmk;
            this.keyUnderZmk = keyUnderZmk;
            this.keyScheme = scheme(keyLengthBytes);
        }

        @Override
        public String toWire() {
            return header + commandCode + keyType
                 + zmkUnderLmk + keyUnderZmk + keyScheme;
        }
    }

    // ── A8 : Export Key ──────────────────────────────────────
    /** A8 — exporte une clé (sous LMK) chiffrée sous un ZMK. */
    public static class A8 extends ThalesCommand {
        public final String keyType;
        public final String zmkUnderLmk;
        public final String keyUnderLmk;
        public final String keyScheme;

        public A8(String header, String keyType, String zmkUnderLmk,
                  String keyUnderLmk, int keyLengthBytes) {
            super(header, "A8");
            this.keyType = keyType;
            this.zmkUnderLmk = zmkUnderLmk;
            this.keyUnderLmk = keyUnderLmk;
            this.keyScheme = scheme(keyLengthBytes);
        }

        @Override
        public String toWire() {
            return header + commandCode + keyType
                 + zmkUnderLmk + keyUnderLmk + keyScheme;
        }
    }

    // ── BU : Generate Key Check Value ────────────────────────
    /** BU — calcule le KCV d'une clé sous LMK. */
    public static class BU extends ThalesCommand {
        public final String keyTypeCode; // "0"=clé sous LMK, etc.
        public final String keyScheme;
        public final String keyUnderLmk;

        public BU(String header, String keyTypeCode, int keyLengthBytes, String keyUnderLmk) {
            super(header, "BU");
            this.keyTypeCode = keyTypeCode;
            this.keyScheme = scheme(keyLengthBytes);
            this.keyUnderLmk = keyUnderLmk;
        }

        @Override
        public String toWire() {
            return header + commandCode + keyTypeCode + keyScheme + keyUnderLmk;
        }
    }
}
