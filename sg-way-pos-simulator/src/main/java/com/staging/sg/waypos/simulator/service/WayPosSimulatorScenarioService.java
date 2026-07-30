package com.staging.sg.waypos.simulator.service;

import com.staging.sg.common.iso.WayPosPrivateData;
import com.staging.sg.waypos.simulator.api.SimulatorScenarioRequest;
import com.staging.sg.waypos.simulator.api.SimulatorScenarioResponse;
import com.staging.sg.waypos.simulator.api.SimulatorTransactionRequest;
import com.staging.sg.waypos.simulator.api.SimulatorTransactionResponse;
import com.staging.sg.waypos.simulator.config.SimulatorProperties;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class WayPosSimulatorScenarioService {
    private final WayPosSimulatorClient client;
    private final SimulatorProperties properties;

    public WayPosSimulatorScenarioService(
            WayPosSimulatorClient client, SimulatorProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    public SimulatorScenarioResponse run(
            String requestedScenario, SimulatorScenarioRequest input)
            throws Exception {
        String scenario = required(
                requestedScenario, "scenario").toUpperCase(Locale.ROOT);
        return switch (scenario) {
            case "PURCHASE" -> purchase(input);
            case "PURCHASE_REPEAT" -> purchaseRepeat(input);
            case "PURCHASE_REVERSAL" -> purchaseReversal(input);
            case "AUTHORIZATION_FINAL_ADVICE" ->
                    authorizationFinalAdvice(input);
            case "PURCHASE_EOD" -> purchaseEod(input);
            case "EXTENDED_P2P" -> extendedP2p(input);
            case "EXTENDED_CARD_CONTROL" -> extendedCardControl(input);
            default -> throw new IllegalArgumentException(
                    "Unsupported scenario " + requestedScenario);
        };
    }

    private SimulatorScenarioResponse purchase(
            SimulatorScenarioRequest input) throws Exception {
        List<SimulatorTransactionResponse> steps = new ArrayList<>();
        SimulatorTransactionResponse initialization = initialize(input);
        steps.add(initialization);
        if (!initialization.approved()) {
            return result("PURCHASE", steps, initialization.batchId());
        }
        steps.add(client.send(financial(input, "0200", "000000",
                input.amount(), null, null, null, null)));
        return result("PURCHASE", steps, initialization.batchId());
    }

    private SimulatorScenarioResponse purchaseRepeat(
            SimulatorScenarioRequest input) throws Exception {
        List<SimulatorTransactionResponse> steps = new ArrayList<>();
        SimulatorTransactionResponse initialization = initialize(input);
        steps.add(initialization);
        if (!initialization.approved()) {
            return result("PURCHASE_REPEAT", steps, initialization.batchId());
        }
        SimulatorTransactionResponse purchase = client.send(financial(
                input, "0200", "000000", input.amount(),
                null, null, null, null));
        steps.add(purchase);
        steps.add(client.repeat(input.terminalId(), input.macEnabled()));
        return result("PURCHASE_REPEAT", steps, initialization.batchId());
    }

    private SimulatorScenarioResponse purchaseReversal(
            SimulatorScenarioRequest input) throws Exception {
        List<SimulatorTransactionResponse> steps = new ArrayList<>();
        SimulatorTransactionResponse initialization = initialize(input);
        steps.add(initialization);
        if (!initialization.approved()) {
            return result("PURCHASE_REVERSAL", steps, initialization.batchId());
        }
        SimulatorTransactionResponse purchase = client.send(financial(
                input, "0200", "000000", input.amount(),
                null, null, null, null));
        steps.add(purchase);
        if (!purchase.approved()) {
            return result("PURCHASE_REVERSAL", steps, initialization.batchId());
        }
        steps.add(client.send(financial(
                input, "0420", "000000", input.amount(), purchase.rrn(),
                "400", "400", originalData("0200", purchase.stan()))));
        return result("PURCHASE_REVERSAL", steps, initialization.batchId());
    }

    private SimulatorScenarioResponse authorizationFinalAdvice(
            SimulatorScenarioRequest input) throws Exception {
        List<SimulatorTransactionResponse> steps = new ArrayList<>();
        SimulatorTransactionResponse initialization = initialize(input);
        steps.add(initialization);
        if (!initialization.approved()) {
            return result(
                    "AUTHORIZATION_FINAL_ADVICE", steps,
                    initialization.batchId());
        }
        SimulatorTransactionResponse authorization = client.send(financial(
                input, "0100", "000000", input.amount(),
                null, null, null, null));
        steps.add(authorization);
        if (!authorization.approved()) {
            return result(
                    "AUTHORIZATION_FINAL_ADVICE", steps,
                    initialization.batchId());
        }
        steps.add(client.send(financial(
                input, "0220", "000000", input.amount(),
                authorization.rrn(), "202", null,
                originalData("0100", authorization.stan()))));
        return result(
                "AUTHORIZATION_FINAL_ADVICE", steps,
                initialization.batchId());
    }

    private SimulatorScenarioResponse purchaseEod(
            SimulatorScenarioRequest input) throws Exception {
        List<SimulatorTransactionResponse> steps = new ArrayList<>();
        SimulatorTransactionResponse initialization = initialize(input);
        steps.add(initialization);
        String batchId = defaultValue(input.batchId(),
                initialization.batchId());
        if (!initialization.approved() || batchId == null) {
            return result("PURCHASE_EOD", steps, batchId);
        }
        SimulatorTransactionResponse purchase = client.send(financial(
                input, "0200", "000000", input.amount(),
                null, null, null, null));
        steps.add(purchase);
        if (!purchase.approved()) {
            return result("PURCHASE_EOD", steps, batchId);
        }
        String totals = reconciliationTotals(
                Long.parseLong(required(input.amount(), "amount")),
                properties.currency());
        steps.add(client.send(system(
                input, "0500", "920000", batchId, totals)));
        return result("PURCHASE_EOD", steps, batchId);
    }

    private SimulatorScenarioResponse extendedP2p(
            SimulatorScenarioRequest input) throws Exception {
        List<SimulatorTransactionResponse> steps = new ArrayList<>();
        SimulatorTransactionResponse initialization = initialize(input);
        steps.add(initialization);
        if (!initialization.approved()) {
            return result("EXTENDED_P2P", steps, initialization.batchId());
        }
        String account = required(input.targetPan(), "targetPan")
                + "=29122010000000000000";
        String privateData = WayPosPrivateData.encode(List.of(
                new WayPosPrivateData.Item("SV", "1.0.0"),
                new WayPosPrivateData.Item("60", account)));
        steps.add(client.send(financial(
                input, "0200", "480000", input.amount(),
                null, null, null, null, privateData)));
        return result("EXTENDED_P2P", steps, initialization.batchId());
    }

    private SimulatorScenarioResponse extendedCardControl(
            SimulatorScenarioRequest input) throws Exception {
        List<SimulatorTransactionResponse> steps = new ArrayList<>();
        SimulatorTransactionResponse initialization = initialize(input);
        steps.add(initialization);
        if (!initialization.approved()) {
            return result(
                    "EXTENDED_CARD_CONTROL", steps,
                    initialization.batchId());
        }
        String inquiry = defaultValue(input.cardControlType(), "24");
        String privateData = WayPosPrivateData.encode(List.of(
                new WayPosPrivateData.Item("SV", "1.0.0"),
                new WayPosPrivateData.Item("62", inquiry)));
        steps.add(client.send(financial(
                input, "0100", "910000", "000000000000",
                null, null, null, null, privateData)));
        return result(
                "EXTENDED_CARD_CONTROL", steps,
                initialization.batchId());
    }

    private SimulatorTransactionResponse initialize(
            SimulatorScenarioRequest input) throws Exception {
        return client.send(system(
                input, "0800", "930000", null, "007SV1.0.0"));
    }

    private static SimulatorTransactionRequest financial(
            SimulatorScenarioRequest input, String mti,
            String processingCode, String amount, String rrn,
            String networkId, String operationSpecificData,
            String originalDataElements) {
        return financial(input, mti, processingCode, amount, rrn,
                networkId, operationSpecificData, originalDataElements,
                "007SV1.0.0");
    }

    private static SimulatorTransactionRequest financial(
            SimulatorScenarioRequest input, String mti,
            String processingCode, String amount, String rrn,
            String networkId, String operationSpecificData,
            String originalDataElements, String privateData) {
        return new SimulatorTransactionRequest(
                mti, processingCode, required(input.pan(), "pan"),
                required(input.expiry(), "expiry"), required(amount, "amount"),
                "051", "00", input.pinBlockHex(), input.emvDataHex(), rrn,
                input.terminalId(), input.merchantId(), input.macEnabled(),
                networkId, null, null, null, null, originalDataElements,
                operationSpecificData, privateData);
    }

    private static SimulatorTransactionRequest system(
            SimulatorScenarioRequest input, String mti,
            String processingCode, String operationSpecificData,
            String privateData) {
        return new SimulatorTransactionRequest(
                mti, processingCode, null, null, null,
                null, null, null, null, null,
                input.terminalId(), input.merchantId(), input.macEnabled(),
                null, null, null, null, null, null,
                operationSpecificData, privateData);
    }

    private static SimulatorScenarioResponse result(
            String scenario, List<SimulatorTransactionResponse> steps,
            String batchId) {
        boolean complete = !steps.isEmpty()
                && steps.stream().allMatch(SimulatorTransactionResponse::approved);
        String status = complete ? "COMPLETED"
                : steps.isEmpty() ? "NOT_STARTED"
                : "STOPPED_ON_RC_" + steps.get(steps.size() - 1).responseCode();
        return new SimulatorScenarioResponse(
                scenario, complete, status, batchId, List.copyOf(steps));
    }

    private static String reconciliationTotals(
            long debitAmount, String currency) {
        String debit = group("D", 1, debitAmount, currency);
        String credit = group("C", 0, 0, currency);
        return WayPosPrivateData.encode(List.of(
                new WayPosPrivateData.Item("SV", "1.0.0"),
                new WayPosPrivateData.Item("PC", "20001"),
                new WayPosPrivateData.Item("28", debit + credit)));
    }

    private static String group(
            String type, int count, long amount, String currency) {
        return type + "1O" + "%03d".formatted(count)
                + required(currency, "currency")
                + "%012d".formatted(amount);
    }

    private static String originalData(String mti, String stan) {
        return mti + stan + "0000000000"
                + "00000000000" + "00000000000";
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value;
    }

    private static String defaultValue(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
