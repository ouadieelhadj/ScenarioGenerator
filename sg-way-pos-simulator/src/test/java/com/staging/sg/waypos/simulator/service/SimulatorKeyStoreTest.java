package com.staging.sg.waypos.simulator.service;

import com.staging.sg.common.iso.WayPosKeyExchangeCodec;
import com.staging.sg.waypos.simulator.config.SimulatorProperties;
import org.jpos.iso.ISOUtil;
import org.junit.jupiter.api.Test;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SimulatorKeyStoreTest {
    @Test
    void importsAnsiX917TakChecksKcvAndActivatesIt() throws Exception {
        String masterHex = "0123456789ABCDEFFEDCBA9876543210";
        String oldTakHex = "00112233445566778899AABBCCDDEEFF";
        String newTakHex = "112233445566778899AABBCCDDEEFF00";
        SimulatorProperties properties = new SimulatorProperties(
                "localhost", 8531, 55, "TERM0001", "MERCHANT0000001",
                "504", "BIN", oldTakHex, "00", "TMK", masterHex,
                "BINARY", "ECB");
        SimulatorKeyStore store = new SimulatorKeyStore(properties);

        byte[] encrypted = encryptUnderMaster(
                ISOUtil.hex2byte(masterHex), ISOUtil.hex2byte(newTakHex));
        var statuses = store.importBlocks(List.of(new WayPosKeyExchangeCodec.KeyBlock(
                "01", "TAK", kcv(ISOUtil.hex2byte(newTakHex)), "T",
                "00", "TMK", encrypted)));

        assertEquals(List.of(
                new WayPosKeyExchangeCodec.KeyStatus("01", "0", "TAK")), statuses);
        assertArrayEquals(ISOUtil.hex2byte(newTakHex), store.activeTak());
    }

    @Test
    void importsWorkingKeysUnderSuppliedTripleLengthTamkAndTpmk() throws Exception {
        String tamkHex = xorComponents(
                "D82E1C5300139702CE6973233E4FE3FEDD975703DCDB3A58",
                "22CABDF3CBF201853698CDB40907B6577A25B9F052066DF4",
                "23555A08DA5D3D3F6D5AC00D2E13B797365802A4987F109E");
        String tpmkHex = xorComponents(
                "651B99ABF7F29BB90F618D55C1AC11F3ABA526A366642D05",
                "9925FDB527E826C2BEC02C7357E4198969073DF749B81350",
                "A9FC4A8930079574B351C538920CCF65A84F5B386EF81335");

        assertEquals("D9B1FBA811BCABB895AB7E9A195BE23E91EAEC5716A24732", tamkHex);
        assertEquals("51C71D", kcv(ISOUtil.hex2byte(tamkHex)));
        assertEquals("55C22E97E01D280F02F0641E0444C71F6AED406C41242D60", tpmkHex);
        assertEquals("95B446", kcv(ISOUtil.hex2byte(tpmkHex)));

        assertWorkingKeyImport(tamkHex, "TAMK", "TAK", "01",
                "112233445566778899AABBCCDDEEFF00");
        assertWorkingKeyImport(tpmkHex, "TPMK", "TPK", "02",
                "AABBCCDDEEFF00112233445566778899");
    }

    @Test
    void importsTakAndTpkInOneExchangeWithSeparateTamkAndTpmk() throws Exception {
        String tamkHex = "D9B1FBA811BCABB895AB7E9A195BE23E91EAEC5716A24732";
        String tpmkHex = "55C22E97E01D280F02F0641E0444C71F6AED406C41242D60";
        String takHex = "112233445566778899AABBCCDDEEFF00";
        String tpkHex = "AABBCCDDEEFF00112233445566778899";
        SimulatorProperties properties = new SimulatorProperties(
                "localhost", 8531, 55, "TERM0001", "MERCHANT0000001",
                "504", "BIN", "00112233445566778899AABBCCDDEEFF",
                "00", "TMK", "", "BINARY", "ECB",
                "01", tamkHex, "02", tpmkHex);
        SimulatorKeyStore store = new SimulatorKeyStore(properties);

        var statuses = store.importBlocks(List.of(
                new WayPosKeyExchangeCodec.KeyBlock(
                        "11", "TAK", kcv(ISOUtil.hex2byte(takHex)), "T",
                        "01", "TAMK", encryptUnderMaster(
                        ISOUtil.hex2byte(tamkHex), ISOUtil.hex2byte(takHex))),
                new WayPosKeyExchangeCodec.KeyBlock(
                        "12", "TPK", kcv(ISOUtil.hex2byte(tpkHex)), "T",
                        "02", "TPMK", encryptUnderMaster(
                        ISOUtil.hex2byte(tpmkHex), ISOUtil.hex2byte(tpkHex)))));

        assertEquals(List.of(
                new WayPosKeyExchangeCodec.KeyStatus("11", "0", "TAK"),
                new WayPosKeyExchangeCodec.KeyStatus("12", "0", "TPK")), statuses);
        assertArrayEquals(ISOUtil.hex2byte(takHex), store.activeTak());
    }

    private static void assertWorkingKeyImport(
            String masterHex, String masterType, String workingType,
            String keyId, String workingKeyHex) throws Exception {
        SimulatorProperties properties = new SimulatorProperties(
                "localhost", 8531, 55, "TERM0001", "MERCHANT0000001",
                "504", "BIN", "00112233445566778899AABBCCDDEEFF",
                "00", masterType, masterHex, "BINARY", "ECB");
        SimulatorKeyStore store = new SimulatorKeyStore(properties);
        byte[] clearWorkingKey = ISOUtil.hex2byte(workingKeyHex);
        byte[] encrypted = encryptUnderMaster(
                ISOUtil.hex2byte(masterHex), clearWorkingKey);

        var statuses = store.importBlocks(List.of(
                new WayPosKeyExchangeCodec.KeyBlock(
                        keyId, workingType, kcv(clearWorkingKey), "T",
                        "00", masterType, encrypted)));

        assertEquals(List.of(new WayPosKeyExchangeCodec.KeyStatus(
                keyId, "0", workingType)), statuses);
        if ("TAK".equals(workingType)) {
            assertArrayEquals(clearWorkingKey, store.activeTak());
        }
    }

    private static String xorComponents(String... components) {
        byte[] result = new byte[ISOUtil.hex2byte(components[0]).length];
        for (String component : components) {
            byte[] value = ISOUtil.hex2byte(component);
            assertEquals(result.length, value.length);
            for (int i = 0; i < result.length; i++) {
                result[i] ^= value[i];
            }
        }
        return ISOUtil.hexString(result);
    }

    private static byte[] encryptUnderMaster(byte[] master, byte[] clear) throws Exception {
        byte[] key24 = expandDesEde(master);
        Cipher cipher = Cipher.getInstance("DESede/ECB/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key24, "DESede"));
        return cipher.doFinal(clear);
    }

    private static String kcv(byte[] clear) throws Exception {
        byte[] key24 = expandDesEde(clear);
        Cipher cipher = Cipher.getInstance("DESede/ECB/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key24, "DESede"));
        return ISOUtil.hexString(cipher.doFinal(new byte[8])).substring(0, 6);
    }

    private static byte[] expandDesEde(byte[] key) {
        if (key.length == 24) {
            return key.clone();
        }
        if (key.length == 16) {
            byte[] key24 = new byte[24];
            System.arraycopy(key, 0, key24, 0, 16);
            System.arraycopy(key, 0, key24, 16, 8);
            return key24;
        }
        throw new IllegalArgumentException("Test key must contain 16 or 24 bytes");
    }
}
