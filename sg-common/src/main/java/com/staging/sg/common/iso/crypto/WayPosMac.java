package com.staging.sg.common.iso.crypto;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;
import java.util.Arrays;

/** OpenWay POS DE64 MAC implementation (BIN/HEX, X9.9/X9.19, 32 bits). */
public final class WayPosMac {

    public enum DataMode { BIN, HEX }

    private WayPosMac() {
    }

    public static byte[] calculate(byte[] key, byte[] messageWithoutDe64, DataMode mode) {
        if (key == null || (key.length != 8 && key.length != 16)) {
            throw new IllegalArgumentException("TAK must be 8 or 16 bytes");
        }
        if (messageWithoutDe64 == null) {
            throw new IllegalArgumentException("MAC data is required");
        }
        try {
            byte[] data = mode == DataMode.HEX
                    ? expandOpenWayHex(messageWithoutDe64)
                    : messageWithoutDe64.clone();
            byte pad = mode == DataMode.HEX ? (byte) 0x30 : 0;
            data = pad(data, pad);
            byte[] state = cbcDes(Arrays.copyOfRange(key, 0, 8), data);
            if (key.length == 16) {
                state = des(Cipher.DECRYPT_MODE, Arrays.copyOfRange(key, 8, 16), state);
                state = des(Cipher.ENCRYPT_MODE, Arrays.copyOfRange(key, 0, 8), state);
            }
            return Arrays.copyOf(state, 4);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Unable to calculate Way POS MAC", e);
        }
    }

    private static byte[] expandOpenWayHex(byte[] input) {
        byte[] output = new byte[input.length * 2];
        for (int i = 0; i < input.length; i++) {
            int value = input[i] & 0xFF;
            output[i * 2] = (byte) (0x30 + (value & 0x0F));
            output[i * 2 + 1] = (byte) (0x30 + ((value >>> 4) & 0x0F));
        }
        return output;
    }

    private static byte[] pad(byte[] input, byte value) {
        int length = ((input.length + 7) / 8) * 8;
        if (length == 0) {
            length = 8;
        }
        byte[] padded = Arrays.copyOf(input, length);
        if (value != 0) {
            Arrays.fill(padded, input.length, length, value);
        }
        return padded;
    }

    private static byte[] cbcDes(byte[] key, byte[] input) throws GeneralSecurityException {
        byte[] state = new byte[8];
        for (int offset = 0; offset < input.length; offset += 8) {
            for (int i = 0; i < 8; i++) {
                state[i] ^= input[offset + i];
            }
            state = des(Cipher.ENCRYPT_MODE, key, state);
        }
        return state;
    }

    private static byte[] des(int mode, byte[] key, byte[] block)
            throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("DES/ECB/NoPadding");
        cipher.init(mode, new SecretKeySpec(key, "DES"));
        return cipher.doFinal(block);
    }
}
