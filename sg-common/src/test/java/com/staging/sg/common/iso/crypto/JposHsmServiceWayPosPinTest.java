package com.staging.sg.common.iso.crypto;

import org.jpos.iso.ISOUtil;
import org.jpos.security.SecureDESKey;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
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

    private static Key form(JposHsmService hsm, String type, String clear)
            throws Exception {
        SecureDESKey key = hsm.formClearKey(type, clear);
        return new Key(
                ISOUtil.hexString(key.getKeyBytes()),
                hsm.computeKcv(ISOUtil.hex2byte(clear)));
    }

    private record Key(String underLmk, String kcv) {}
}
