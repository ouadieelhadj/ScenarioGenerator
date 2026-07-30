package com.staging.sg.waypos.simulator.service;

import com.staging.sg.common.iso.WayPosPrivateData;
import com.staging.sg.waypos.simulator.api.SimulatorScenarioRequest;
import com.staging.sg.waypos.simulator.api.SimulatorScenarioResponse;
import com.staging.sg.waypos.simulator.api.SimulatorTransactionRequest;
import com.staging.sg.waypos.simulator.api.SimulatorTransactionResponse;
import com.staging.sg.waypos.simulator.config.SimulatorProperties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WayPosSimulatorScenarioServiceTest {

    @Test
    void purchaseReversalCarriesOriginalRrnAndReferences() throws Exception {
        WayPosSimulatorClient client = mock(WayPosSimulatorClient.class);
        when(client.send(any())).thenReturn(
                approved("0800", "0810", "000001", null, "000123"),
                approved("0200", "0210", "000002", "123456000002", null),
                approved("0420", "0430", "000003", "123456000002", null));
        WayPosSimulatorScenarioService service =
                new WayPosSimulatorScenarioService(client, properties());

        SimulatorScenarioResponse result =
                service.run("purchase_reversal", input());

        assertTrue(result.completed());
        ArgumentCaptor<SimulatorTransactionRequest> sent =
                ArgumentCaptor.forClass(SimulatorTransactionRequest.class);
        verify(client, times(3)).send(sent.capture());
        SimulatorTransactionRequest reversal = sent.getAllValues().get(2);
        assertEquals("0420", reversal.mti());
        assertEquals("400", reversal.networkId());
        assertEquals("400", reversal.operationSpecificData());
        assertEquals("123456000002", reversal.rrn());
        assertEquals(42, reversal.originalDataElements().length());
    }

    @Test
    void purchaseRepeatUsesClientExactRepeat() throws Exception {
        WayPosSimulatorClient client = mock(WayPosSimulatorClient.class);
        when(client.send(any())).thenReturn(
                approved("0800", "0810", "000001", null, "000123"),
                approved("0200", "0210", "000002", "123456000002", null));
        when(client.repeat("TERM0001", true)).thenReturn(
                approved("0201", "0211", "000002", "123456000002", null));
        WayPosSimulatorScenarioService service =
                new WayPosSimulatorScenarioService(client, properties());

        SimulatorScenarioResponse result =
                service.run("purchase_repeat", input());

        assertTrue(result.completed());
        assertEquals(3, result.steps().size());
        verify(client).repeat("TERM0001", true);
    }

    @Test
    void purchaseEodBuildsDebitAndZeroCreditTotals() throws Exception {
        WayPosSimulatorClient client = mock(WayPosSimulatorClient.class);
        when(client.send(any())).thenReturn(
                approved("0800", "0810", "000001", null, "000123"),
                approved("0200", "0210", "000002", "123456000002", null),
                approved("0500", "0510", "000003", null, "000123"));
        WayPosSimulatorScenarioService service =
                new WayPosSimulatorScenarioService(client, properties());

        SimulatorScenarioResponse result =
                service.run("purchase_eod", input());

        assertTrue(result.completed());
        ArgumentCaptor<SimulatorTransactionRequest> sent =
                ArgumentCaptor.forClass(SimulatorTransactionRequest.class);
        verify(client, times(3)).send(sent.capture());
        SimulatorTransactionRequest reconciliation =
                sent.getAllValues().get(2);
        assertEquals("0500", reconciliation.mti());
        assertEquals("000123", reconciliation.operationSpecificData());
        List<WayPosPrivateData.Item> totals =
                WayPosPrivateData.decode(reconciliation.privateData());
        String groups = totals.stream()
                .filter(item -> "28".equals(item.tableId()))
                .findFirst().orElseThrow().value();
        assertEquals("D1O001504000000001000"
                + "C1O000504000000000000", groups);
    }

    @Test
    void extendedP2pEncodesTargetAccountInTable60() throws Exception {
        WayPosSimulatorClient client = mock(WayPosSimulatorClient.class);
        when(client.send(any())).thenReturn(
                approved("0800", "0810", "000001", null, "000123"),
                approved("0200", "0210", "000002", "123456000002", null));
        WayPosSimulatorScenarioService service =
                new WayPosSimulatorScenarioService(client, properties());

        SimulatorScenarioResponse result =
                service.run("extended_p2p", input());

        assertTrue(result.completed());
        ArgumentCaptor<SimulatorTransactionRequest> sent =
                ArgumentCaptor.forClass(SimulatorTransactionRequest.class);
        verify(client, times(2)).send(sent.capture());
        SimulatorTransactionRequest p2p = sent.getAllValues().get(1);
        assertEquals("480000", p2p.processingCode());
        String target = WayPosPrivateData.decode(p2p.privateData()).stream()
                .filter(item -> "60".equals(item.tableId()))
                .findFirst().orElseThrow().value();
        assertTrue(target.startsWith("5413330089012345="));
    }

    @Test
    void authorizationFinalAdvicePreservesOriginalReference()
            throws Exception {
        WayPosSimulatorClient client = mock(WayPosSimulatorClient.class);
        when(client.send(any())).thenReturn(
                approved("0800", "0810", "000001", null, "000123"),
                approved("0100", "0110", "000002", "123456000002", null),
                approved("0220", "0230", "000003", "123456000002", null));
        WayPosSimulatorScenarioService service =
                new WayPosSimulatorScenarioService(client, properties());

        SimulatorScenarioResponse result =
                service.run("authorization_final_advice", input());

        assertTrue(result.completed());
        ArgumentCaptor<SimulatorTransactionRequest> sent =
                ArgumentCaptor.forClass(SimulatorTransactionRequest.class);
        verify(client, times(3)).send(sent.capture());
        SimulatorTransactionRequest advice = sent.getAllValues().get(2);
        assertEquals("0220", advice.mti());
        assertEquals("202", advice.networkId());
        assertEquals("123456000002", advice.rrn());
        assertEquals(42, advice.originalDataElements().length());
    }

    @Test
    void extendedCardControlEncodesInquiryTypeInTable62()
            throws Exception {
        WayPosSimulatorClient client = mock(WayPosSimulatorClient.class);
        when(client.send(any())).thenReturn(
                approved("0800", "0810", "000001", null, "000123"),
                approved("0100", "0110", "000002", "123456000002", null));
        WayPosSimulatorScenarioService service =
                new WayPosSimulatorScenarioService(client, properties());

        SimulatorScenarioResponse result =
                service.run("extended_card_control", input());

        assertTrue(result.completed());
        ArgumentCaptor<SimulatorTransactionRequest> sent =
                ArgumentCaptor.forClass(SimulatorTransactionRequest.class);
        verify(client, times(2)).send(sent.capture());
        SimulatorTransactionRequest control = sent.getAllValues().get(1);
        String type = WayPosPrivateData.decode(control.privateData()).stream()
                .filter(item -> "62".equals(item.tableId()))
                .findFirst().orElseThrow().value();
        assertEquals("24", type);
    }

    private static SimulatorScenarioRequest input() {
        return new SimulatorScenarioRequest(
                "5321962145453348", "2912", "000000001000",
                "5413330089012345", "0011223344556677",
                "9F26081122334455667788", "TERM0001",
                "MERCHANT0000001", true, null, "24");
    }

    private static SimulatorTransactionResponse approved(
            String requestMti, String responseMti, String stan,
            String rrn, String batchId) {
        return new SimulatorTransactionResponse(
                requestMti, responseMti, stan, rrn, "00", "ABC123",
                true, true, 5L, batchId, null);
    }

    private static SimulatorProperties properties() {
        return new SimulatorProperties(
                "localhost", 8531, 55, "TERM0001", "MERCHANT0000001",
                "504", "BIN", null, "00", "TMK", null,
                "BINARY", "ECB");
    }
}
