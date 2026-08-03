package com.staging.sg.waypos.simulator.service;

import com.staging.sg.common.iso.WayPosMessageValidator;
import com.staging.sg.common.iso.WayPosKeyExchangeCodec;
import com.staging.sg.common.iso.WayPosPackager;
import com.staging.sg.common.iso.WayPosPrivateData;
import com.staging.sg.waypos.simulator.api.SimulatorTransactionRequest;
import com.staging.sg.waypos.simulator.config.SimulatorProperties;
import org.jpos.iso.ISOMsg;
import org.jpos.iso.ISOUtil;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WayPosSimulatorClientMessageTest {

    @Test
    void buildsBasicPurchaseWithPinEmvAndPrivateData() throws Exception {
        WayPosSimulatorClient client = client();
        SimulatorTransactionRequest input = request(
                "0200", "000000", "000000001000");

        ISOMsg message = client.build(input, "000001");

        WayPosMessageValidator.validateRequest(message);
        assertEquals("0200", message.getMTI());
        assertEquals("000001", message.getString(11));
        assertEquals("TERM0001", message.getString(41));
        assertArrayEquals(ISOUtil.hex2byte(input.pinBlockHex()),
                message.getBytes(52));
        assertArrayEquals(ISOUtil.hex2byte(input.emvDataHex()),
                message.getBytes(55));
    }

    @Test
    void canBuildRealTerminalShapeWithoutRequestRrnOrMerchantId()
            throws Exception {
        WayPosSimulatorClient client = client();
        SimulatorTransactionRequest input = new SimulatorTransactionRequest(
                "0200", "000000", "5321962145453348", "2912",
                "000000001000", "051", "00", null, null, null,
                "TERM0001", null, false, null, null, null, null,
                null, null, null, "007SV1.0.0");

        ISOMsg message = client.build(input, "000009");

        WayPosMessageValidator.validateRequest(message);
        assertFalse(message.hasField(37));
        assertFalse(message.hasField(42));
    }

    @Test
    void buildsUniversalReversalWithNetworkAndOriginalData()
            throws Exception {
        WayPosSimulatorClient client = client();
        SimulatorTransactionRequest input = new SimulatorTransactionRequest(
                "0420", "000000", "5321962145453348", null,
                "000000001000", null, null, null, null,
                "123456000001", "TERM0001", "MERCHANT0000001", false,
                "402", null, null, null, null,
                "020000000107301130001234567890123456789012",
                "402", null);

        ISOMsg message = client.build(input, "000002");

        WayPosMessageValidator.validateRequest(message);
        assertEquals("402", message.getString(24));
        assertEquals("402", message.getString(60));
        assertEquals(input.originalDataElements(), message.getString(56));
    }

    @Test
    void buildsReconciliationWithExactPrivateTotals() throws Exception {
        WayPosSimulatorClient client = client();
        String privateData = WayPosPrivateData.encode(List.of(
                new WayPosPrivateData.Item("SV", "1.0.0"),
                new WayPosPrivateData.Item("PC", "20001"),
                new WayPosPrivateData.Item(
                        "28", group("D", 1, 1_000)
                                + group("C", 0, 0))));
        SimulatorTransactionRequest input = new SimulatorTransactionRequest(
                "0500", "920000", null, null, null, null, null,
                null, null, null, "TERM0001", "MERCHANT0000001",
                false, null, null, null, null, null, null,
                "000123", privateData);

        ISOMsg message = client.build(input, "000003");

        WayPosMessageValidator.validateRequest(message);
        assertEquals("000123", message.getString(60));
        assertEquals(privateData, message.getString(63));
    }

    @Test
    void buildsExtendedP2pAndCardControlPrivateTables() throws Exception {
        WayPosSimulatorClient client = client();
        String p2pData = WayPosPrivateData.encode(List.of(
                new WayPosPrivateData.Item("SV", "1.0.0"),
                new WayPosPrivateData.Item(
                        "60", "5413330089012345=29122010000000000000")));
        ISOMsg p2p = client.build(new SimulatorTransactionRequest(
                "0200", "480000", "5321962145453348", "2912",
                "000000001000", "051", "00", null, null,
                "123456000001", "TERM0001", "MERCHANT0000001",
                false, null, null, null, null, null, null,
                null, p2pData), "000004");
        WayPosMessageValidator.validateRequest(p2p);

        String controlData = WayPosPrivateData.encode(List.of(
                new WayPosPrivateData.Item("SV", "1.0.0"),
                new WayPosPrivateData.Item("62", "24")));
        ISOMsg control = client.build(new SimulatorTransactionRequest(
                "0100", "910000", "5321962145453348", "2912",
                "000000000000", "051", "00", null, null,
                "123456000002", "TERM0001", "MERCHANT0000001",
                false, null, null, null, null, null, null,
                null, controlData), "000005");
        WayPosMessageValidator.validateRequest(control);
    }

    @Test
    void rejectsSystemMessageMissingOperationSpecificData() {
        WayPosSimulatorClient client = client();
        SimulatorTransactionRequest input = new SimulatorTransactionRequest(
                "0500", "920000", null, null, null, null, null,
                null, null, null, "TERM0001", "MERCHANT0000001",
                false, null, null, null, null, null, null,
                null, "007SV1.0.0");

        assertThrows(IllegalArgumentException.class,
                () -> client.build(input, "000006"));
    }

    @Test
    void repeatKeepsStanRrnAndPayloadAndOnlyChangesMti() throws Exception {
        WayPosSimulatorClient client = client();
        ISOMsg original = client.build(
                request("0200", "000000", "000000001000"), "000007");
        client.remember(original);

        ISOMsg repeat = client.prepareRepeat("TERM0001", false);

        assertEquals("0201", repeat.getMTI());
        assertEquals(original.getString(11), repeat.getString(11));
        assertEquals(original.getString(37), repeat.getString(37));
        assertEquals(original.getString(2), repeat.getString(2));
        assertEquals(original.getString(4), repeat.getString(4));
        assertEquals(original.getString(55), repeat.getString(55));
    }

    @Test
    void responseValidationRejectsMismatchedStan() throws Exception {
        WayPosSimulatorClient client = client();
        ISOMsg request = client.build(
                request("0200", "000000", "000000001000"), "000008");
        ISOMsg response = new ISOMsg();
        response.setPackager(new WayPosPackager());
        response.setMTI("0210");
        for (int field : new int[] {2, 3, 4, 41, 49}) {
            response.set(field, request.getString(field));
        }
        response.set(11, "999999");

        assertThrows(IllegalStateException.class,
                () -> WayPosSimulatorClient.validateResponse(
                        request, response));
    }

    @Test
    void buildsRealKeyConfirmationAs0800Processing930000() throws Exception {
        SimulatorKeyStore keyStore = mock(SimulatorKeyStore.class);
        when(keyStore.statuses()).thenReturn(List.of(
                new WayPosKeyExchangeCodec.KeyStatus("27", "0", "TPK"),
                new WayPosKeyExchangeCodec.KeyStatus("27", "0", "TAK")));
        WayPosSimulatorClient client = client(keyStore);

        ISOMsg request = client.buildKeyConfirmation("000010");

        WayPosMessageValidator.validateRequest(request);
        assertEquals("0800", request.getMTI());
        assertEquals("930000", request.getString(3));
        assertEquals(2, WayPosKeyExchangeCodec.decodeStatuses(
                request.getBytes(48)).size());
        assertFalse(request.hasField(42));
        assertFalse(request.hasField(49));
    }

    @Test
    void buildsInitialRkiLikeRealTerminalWithMasterKcvsAndNoMac()
            throws Exception {
        SimulatorKeyStore keyStore = mock(SimulatorKeyStore.class);
        when(keyStore.initialMasterKeyStatuses()).thenReturn(List.of(
                new WayPosKeyExchangeCodec.KeyStatusDetails(
                        "00", "0", "TAMK", "A1B2C3", "C", "0"),
                new WayPosKeyExchangeCodec.KeyStatusDetails(
                        "00", "0", "TPMK", "D4E5F6", "C", "0")));
        WayPosSimulatorClient client = client(keyStore);

        ISOMsg request = client.buildInitialKeyChange("000011");

        WayPosMessageValidator.validateRequest(request);
        assertEquals("0800", request.getMTI());
        assertEquals("960000", request.getString(3));
        assertEquals(2, WayPosKeyExchangeCodec.decodeStatusDetails(
                request.getBytes(48)).size());
        assertFalse(request.hasField(64));
    }

    private static WayPosSimulatorClient client() {
        return client(mock(SimulatorKeyStore.class));
    }

    private static WayPosSimulatorClient client(SimulatorKeyStore keyStore) {
        SimulatorProperties properties = new SimulatorProperties(
                "localhost", 8531, 55, "TERM0001", "MERCHANT0000001",
                "504", "BIN", null, "00", "TMK", null,
                "BINARY", "ECB");
        return new WayPosSimulatorClient(
                properties, new WayPosPackager(), new SimulatorStan(),
                keyStore);
    }

    private static SimulatorTransactionRequest request(
            String mti, String processingCode, String amount) {
        return new SimulatorTransactionRequest(
                mti, processingCode, "5321962145453348", "2912",
                amount, "051", "00", "0011223344556677",
                "9F26081122334455667788", "123456000001",
                "TERM0001", "MERCHANT0000001", false,
                null, null, null, null, null, null, null,
                "007SV1.0.0");
    }

    private static String group(
            String type, int count, long amount) {
        return type + "1O" + "%03d".formatted(count)
                + "504" + "%012d".formatted(amount);
    }
}
