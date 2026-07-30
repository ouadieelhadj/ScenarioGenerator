package com.staging.sg.common.iso.crypto;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;

/**
 * Calculateur autonome pour le buffer exact de la capture Thales M6
 * du message SWAM 1804/899 (STAN 580401).
 *
 * <p>La ZMK n'est jamais codee en dur ni affichee. Elle doit etre fournie
 * dans la variable d'environnement {@code SWAM_ZMK_CLEAR}. Le programme
 * calcule le MAC avec cette ZMK, dechiffre la cle transportee dans P10,
 * puis refait le calcul avec cette seconde cle.</p>
 */
public final class SwamM6CaptureMacCalculator {

    private static final String EXPECTED_DE128 = "E47C1B48";
    private static final String P16_PEK_UNDER_ZMK =
            "8CBC1D6722B5C7756869761F45EC2085";

    private static final String BUFFER =
            "2607291511"
                    + "580401"
                    + "260729151123"
                    + "899"
                    + "0000"
                    + "06" + "300853"
                    + "621015260729"
                    + "039" + "P10033XE81672E4E1B4EC5FC92F99FFA99CB072";

    private SwamM6CaptureMacCalculator() {
    }

    public static void main(String[] args) throws Exception {
        String zmkHex = System.getenv("SWAM_ZMK_CLEAR");
        if (zmkHex == null || zmkHex.isBlank()) {
            System.err.println(
                    "SWAM_ZMK_CLEAR absente. Saisir la ZMK uniquement dans "
                            + "le terminal, sans la placer dans Git.");
            System.exit(2);
        }

        byte[] zmk = hexToBytes(zmkHex.trim());
        byte[] buffer = BUFFER.getBytes(StandardCharsets.US_ASCII);
        if (buffer.length != 0x61) {
            throw new IllegalStateException(
                    "Buffer capture attendu a 0x0061 octets, obtenu "
                            + buffer.length);
        }

        String p10CipherHex = extractP10Ciphertext(BUFFER);
        byte[] p10Clear = decryptUnderZmk(hexToBytes(p10CipherHex), zmk);
        byte[] pekClear =
                decryptUnderZmk(hexToBytes(P16_PEK_UNDER_ZMK), zmk);

        System.out.println("Capture     : MTI=1804 DE24=899 STAN=580401");
        System.out.println("Commande M6 : 0|0|01|1|003|U...|0061|buffer");
        System.out.println("Buffer      : longueur=0061 hex (97 octets)");
        System.out.println("SHA-256     : " + sha256(buffer));
        printResult("ZMK/TAK 003", zmk, buffer);
        printResult("Cle P10 recue", p10Clear, buffer);
        printResult("PEK P16 recue", pekClear, buffer);
        System.out.println("DE128 recu  : " + EXPECTED_DE128);

        Arrays.fill(zmk, (byte) 0);
        Arrays.fill(p10Clear, (byte) 0);
        Arrays.fill(pekClear, (byte) 0);
    }

    private static void printResult(String label, byte[] key, byte[] buffer)
            throws Exception {
        String firstBlock = bytesToHex(firstCbcBlock(key, buffer));
        String mac8 = bytesToHex(macAlgorithm01(key, buffer));
        String de128 = mac8.substring(0, 8);
        String desK1Mac8 = bytesToHex(macDesK1(key, buffer));
        byte[] chained4 = appendAscii(buffer, de128);
        String chained4Mac8 =
                bytesToHex(macAlgorithm01(key, chained4));
        byte[] chained8 = appendAscii(buffer, mac8);
        String chained8Mac8 =
                bytesToHex(macAlgorithm01(key, chained8));

        System.out.printf(
                "%-13s: KCV=%s MAC8=%s DE128=%s matchRecu=%s%n",
                label, kcv(key), mac8, de128,
                de128.equalsIgnoreCase(EXPECTED_DE128));
        System.out.printf(
                "%-13s  premier bloc CBC=%s premiers-4o=%s matchRecu=%s%n",
                "", firstBlock, firstBlock.substring(0, 8),
                firstBlock.substring(0, 8)
                        .equalsIgnoreCase(EXPECTED_DE128));
        System.out.printf(
                "%-13s  DES-CBC avec K1: MAC8=%s DE128=%s matchRecu=%s%n",
                "", desK1Mac8, desK1Mac8.substring(0, 8),
                desK1Mac8.substring(0, 8)
                        .equalsIgnoreCase(EXPECTED_DE128));
        System.out.printf(
                "%-13s  chaine-4o: len=%d MAC8=%s DE128=%s matchRecu=%s%n",
                "", chained4.length, chained4Mac8,
                chained4Mac8.substring(0, 8),
                chained4Mac8.substring(0, 8)
                        .equalsIgnoreCase(EXPECTED_DE128));
        System.out.printf(
                "%-13s  chaine-8o: len=%d MAC8=%s DE128=%s matchRecu=%s%n",
                "", chained8.length, chained8Mac8,
                chained8Mac8.substring(0, 8),
                chained8Mac8.substring(0, 8)
                        .equalsIgnoreCase(EXPECTED_DE128));
    }

    private static byte[] appendAscii(byte[] buffer, String hex) {
        byte[] suffix = hex.getBytes(StandardCharsets.US_ASCII);
        byte[] result = Arrays.copyOf(
                buffer, buffer.length + suffix.length);
        System.arraycopy(suffix, 0, result, buffer.length, suffix.length);
        return result;
    }

    private static byte[] macAlgorithm01(byte[] key, byte[] data)
            throws Exception {
        byte[] encrypted = encryptCbc(key, data);
        return Arrays.copyOfRange(
                encrypted, encrypted.length - 8, encrypted.length);
    }

    private static byte[] firstCbcBlock(byte[] key, byte[] data)
            throws Exception {
        return Arrays.copyOfRange(encryptCbc(key, data), 0, 8);
    }

    private static byte[] macDesK1(byte[] key, byte[] data)
            throws Exception {
        byte[] padded = Arrays.copyOf(data, ((data.length + 7) / 8) * 8);
        Cipher cipher = Cipher.getInstance("DES/CBC/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE,
                new SecretKeySpec(Arrays.copyOfRange(key, 0, 8), "DES"),
                new IvParameterSpec(new byte[8]));
        byte[] encrypted = cipher.doFinal(padded);
        return Arrays.copyOfRange(
                encrypted, encrypted.length - 8, encrypted.length);
    }

    private static byte[] encryptCbc(byte[] key, byte[] data)
            throws Exception {
        byte[] padded = Arrays.copyOf(data, ((data.length + 7) / 8) * 8);
        Cipher cipher = Cipher.getInstance("DESede/CBC/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE,
                new SecretKeySpec(expandDesEdeKey(key), "DESede"),
                new IvParameterSpec(new byte[8]));
        return cipher.doFinal(padded);
    }

    private static byte[] decryptUnderZmk(byte[] encrypted, byte[] zmk)
            throws Exception {
        Cipher cipher = Cipher.getInstance("DESede/ECB/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE,
                new SecretKeySpec(expandDesEdeKey(zmk), "DESede"));
        return cipher.doFinal(encrypted);
    }

    private static String kcv(byte[] key) throws Exception {
        Cipher cipher = Cipher.getInstance("DESede/ECB/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE,
                new SecretKeySpec(expandDesEdeKey(key), "DESede"));
        return bytesToHex(cipher.doFinal(new byte[8])).substring(0, 6);
    }

    private static byte[] expandDesEdeKey(byte[] key) {
        if (key.length == 24) {
            return key.clone();
        }
        if (key.length == 16) {
            byte[] expanded = new byte[24];
            System.arraycopy(key, 0, expanded, 0, 16);
            System.arraycopy(key, 0, expanded, 16, 8);
            return expanded;
        }
        throw new IllegalArgumentException(
                "Cle double/triple longueur attendue (16 ou 24 octets)");
    }

    private static String extractP10Ciphertext(String buffer) {
        String marker = "P10033X";
        int start = buffer.indexOf(marker);
        if (start < 0) {
            throw new IllegalArgumentException("Tag P10 absent du buffer");
        }
        start += marker.length();
        int end = start + 32;
        if (end > buffer.length()) {
            throw new IllegalArgumentException("Valeur P10 incomplete");
        }
        return buffer.substring(start, end);
    }

    private static String sha256(byte[] data) throws Exception {
        return bytesToHex(MessageDigest.getInstance("SHA-256").digest(data));
    }

    private static byte[] hexToBytes(String value) {
        if ((value.length() & 1) != 0) {
            throw new IllegalArgumentException("Valeur hexadecimale impaire");
        }
        byte[] result = new byte[value.length() / 2];
        for (int i = 0; i < result.length; i++) {
            result[i] = (byte) Integer.parseInt(
                    value.substring(i * 2, i * 2 + 2), 16);
        }
        return result;
    }

    private static String bytesToHex(byte[] value) {
        StringBuilder result = new StringBuilder(value.length * 2);
        for (byte b : value) {
            result.append(String.format("%02X", b & 0xFF));
        }
        return result.toString();
    }
}
