package com.staging.sg.common.iso.crypto;

import org.jpos.iso.ISOUtil;
import org.jpos.security.SecureDESKey;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JposHsmServiceWayPosPinTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void verifiesPvvWithoutExposingClearPin() throws Exception {
        JposHsmService hsm = new JposHsmService();
        ReflectionTestUtils.setField(
                hsm, "lmkFile", temporaryDirectory.resolve("waypos-test.lmk").toString());
        ReflectionTestUtils.setField(hsm, "lmkRebuild", true);
        hsm.init();

        Key tpk = form(hsm, "TPK", "0123456789ABCDEFFEDCBA9876543210");
        Key pvkA = form(hsm, "PVK", "0011223344556677");
        Key pvkB = form(hsm, "PVK", "8899AABBCCDDEEFF");
        String pan = "5321962145453348";
        byte[] pinBlock = hsm.encryptPinBlockUnderTpk(
                "1234", pan, tpk.underLmk(), tpk.kcv(), 16);
        String pvv = hsm.calculatePinPvv(
                pinBlock, pan, tpk.underLmk(), tpk.kcv(), 16,
                pvkA.underLmk(), pvkA.kcv(),
                pvkB.underLmk(), pvkB.kcv(), 1);

        assertTrue(hsm.verifyPinPvv(
                pinBlock, pan, tpk.underLmk(), tpk.kcv(), 16,
                pvkA.underLmk(), pvkA.kcv(),
                pvkB.underLmk(), pvkB.kcv(), 1, pvv));
        assertFalse(hsm.verifyPinPvv(
                pinBlock, pan, tpk.underLmk(), tpk.kcv(), 16,
                pvkA.underLmk(), pvkA.kcv(),
                pvkB.underLmk(), pvkB.kcv(), 1, "9999"));
    }

    @Test
    void validatesWorkingKeyKcvInsideHsmBoundary() throws Exception {
        JposHsmService hsm = new JposHsmService();
        ReflectionTestUtils.setField(
                hsm, "lmkFile",
                temporaryDirectory.resolve("waypos-key-validation.lmk").toString());
        ReflectionTestUtils.setField(hsm, "lmkRebuild", true);
        hsm.init();

        Key tak = form(hsm, "TAK", "0123456789ABCDEFFEDCBA9876543210");

        assertTrue(hsm.validateKeyUnderLmk(
                "TAK", tak.underLmk(), tak.kcv(), 16));
        assertFalse(hsm.validateKeyUnderLmk(
                "TAK", tak.underLmk(), "000000", 16));
    }

    @Test
    void translatesSimulatorIso0PinBlockFromTerminalTpkToDestinationPek()
            throws Exception {
        JposHsmService hsm = new JposHsmService();
        ReflectionTestUtils.setField(
                hsm, "lmkFile",
                temporaryDirectory.resolve("waypos-pin-translation.lmk").toString());
        ReflectionTestUtils.setField(hsm, "lmkRebuild", true);
        hsm.init();

        String clearTpk = "0123456789ABCDEFFEDCBA9876543210";
        String clearPek = "0BAECB044F57F25723BA7C75737C7989";
        Key tpk = form(hsm, "TPK", clearTpk);
        Key pek = form(hsm, "PEK", clearPek);
        String pan = "5321962145453348";
        String pin = "4315";
        byte[] simulatorPinBlock = encryptIso0(pin, pan, clearTpk);

        byte[] translated = hsm.translatePinBlock(
                simulatorPinBlock, pan,
                tpk.underLmk(), tpk.kcv(), 16,
                pek.underLmk(), pek.kcv(), 16);

        assertEquals(pin, hsm.decryptPinBlock(
                translated, pan, pek.underLmk(), pek.kcv(), 16));
    }

    @Test
    void tr31BlockContainsTheSameTakStoredUnderLocalLmk() throws Exception {
        JposHsmService hsm = new JposHsmService();
        ReflectionTestUtils.setField(
                hsm, "lmkFile",
                temporaryDirectory.resolve("waypos-tr31.lmk").toString());
        ReflectionTestUtils.setField(hsm, "lmkRebuild", true);
        hsm.init();
        String kbpk = "00112233445566778899AABBCCDDEEFF"
                + "0102030405060708";

        JposHsmService.Tr31KeyResult generated =
                hsm.generateTr31WorkingKey("TAK", 16, kbpk, "28");

        String block = new String(generated.keyBlockAscii(),
                java.nio.charset.StandardCharsets.US_ASCII);
        byte[] fromBlock = Tr31VersionDKeyBlock.unwrap(
                ISOUtil.hex2byte(kbpk), block);
        byte[] fromLmk = hsm.exposeClearKey(
                "TAK", generated.keyUnderLmkHex(), generated.kcv(), 16);
        assertEquals(112, generated.keyBlockAscii().length);
        assertTrue(block.startsWith("D0112M3TN28N0000"));
        assertArrayEquals(fromLmk, fromBlock);
    }

    private static Key form(JposHsmService hsm, String type, String clear)
            throws Exception {
        SecureDESKey key = hsm.formClearKey(type, clear);
        return new Key(
                ISOUtil.hexString(key.getKeyBytes()),
                hsm.computeKcv(ISOUtil.hex2byte(clear)));
    }

    private static byte[] encryptIso0(String pin, String pan, String clearTpk)
            throws Exception {
        String pinField = "0" + Integer.toHexString(pin.length()).toUpperCase()
                + pin;
        pinField = pinField + "F".repeat(16 - pinField.length());
        String pan12 = pan.substring(pan.length() - 13, pan.length() - 1);
        byte[] clearBlock = ISOUtil.xor(
                ISOUtil.hex2byte(pinField), ISOUtil.hex2byte("0000" + pan12));

        byte[] key16 = ISOUtil.hex2byte(clearTpk);
        byte[] key24 = new byte[24];
        System.arraycopy(key16, 0, key24, 0, 16);
        System.arraycopy(key16, 0, key24, 16, 8);
        Cipher cipher = Cipher.getInstance("DESede/ECB/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key24, "DESede"));
        return cipher.doFinal(clearBlock);
    }

    private record Key(String underLmk, String kcv) {}
}
