package com.staging.sg.switchlab.bff.api;

import com.staging.sg.switchlab.bff.config.SwitchLabCorrelationFilter;
import com.staging.sg.switchlab.bff.service.SwitchLabGatewayService;
import com.staging.sg.switchlab.bff.service.SwitchLabPosExecutionService;
import com.staging.sg.switchlab.bff.service.SwitchLabPosGatewayService;
import com.staging.sg.switchlab.contracts.SwitchLabMtipSentinelRequest;
import com.staging.sg.switchlab.contracts.SwitchLabPosExecution;
import com.staging.sg.switchlab.contracts.SwitchLabPosScenarioDefinition;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Collections;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/switchlab/v1/pos")
public class SwitchLabPosController {
    private static final String SENTINEL = "MCD01.Test.01.Scenario.01";
    private static final String EMV_DATA = "9F3303E06008950500000480019F37045424CC419F2608FCC9477BBF5C91999F36020003820219809C01009F1A0206439A032604079F02060000000080005F2A0205049F03060000000000009F2701809F34034203009F3501228407A00000000410109F090200029F10120110A00000000000000000000000000000FF";

    private final SwitchLabGatewayService authentication;
    private final SwitchLabPosGatewayService pos;
    private final SwitchLabPosExecutionService history;

    public SwitchLabPosController(SwitchLabGatewayService authentication,
                                  SwitchLabPosGatewayService pos,
                                  SwitchLabPosExecutionService history) {
        this.authentication = authentication;
        this.pos = pos;
        this.history = history;
    }

    @GetMapping("/catalog")
    public List<SwitchLabPosScenarioDefinition> catalog(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        requireAuthorized(authorization);
        return List.of(
                scenario("PURCHASE", "Purchase", "Financial purchase with initialization and optional key exchange"),
                scenario("PURCHASE_REPEAT", "Purchase and repeat", "Purchase followed by repeat of the last transaction"),
                scenario("PURCHASE_REVERSAL", "Purchase and reversal", "Approved purchase followed by a reversal"),
                scenario("AUTHORIZATION_FINAL_ADVICE", "Authorization and final advice", "Authorization followed by completion advice"),
                scenario("PURCHASE_EOD", "Purchase and EOD", "Purchase included in an end-of-day sequence"),
                scenario("EXTENDED_P2P", "Extended P2P", "Extended person-to-person transaction scenario"),
                scenario("EXTENDED_CARD_CONTROL", "Extended card control", "Extended card-control scenario"),
                new SwitchLabPosScenarioDefinition(SENTINEL,
                "Mastercard contactless purchase approved",
                "Validate an approved contactless purchase and the required ISO 8583 fields",
                "SIMULATABLE_WITH_CERTIFICATION_KEYS", true,
                List.of("Response MTI 0110", "Response code 00", "Approved = true")));
    }

    @GetMapping("/history")
    public List<SwitchLabPosExecution> history(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
                                               @RequestParam(defaultValue = "50") int limit) {
        requireAuthorized(authorization);
        return history.latest(limit);
    }

    @PostMapping("/transactions")
    public SwitchLabPosExecution transaction(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
                                             @RequestBody Map<String, Object> request,
                                             HttpServletRequest servletRequest) {
        requireAuthorized(authorization);
        return execute("TRANSACTION", summary(request), "Response received", servletRequest,
                () -> pos.post("/api/simulator/v1/transactions", request), response -> !response.containsKey("error"));
    }

    @PostMapping("/field-map")
    public SwitchLabPosExecution fieldMap(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
                                         @RequestBody Map<String, Object> request,
                                         HttpServletRequest servletRequest) {
        requireAuthorized(authorization);
        Map<String, Object> safe = new LinkedHashMap<>();
        safe.put("mti", request.get("mti"));
        safe.put("fieldNumbers", keys(request.get("fields")));
        safe.put("binaryFieldNumbers", keys(request.get("binaryFields")));
        safe.put("unsetFields", request.getOrDefault("unsetFields", List.of()));
        safe.put("macEnabled", request.get("macEnabled"));
        safe.put("validate", request.get("validate"));
        return execute("FIELD_MAP", safe, "Response received", servletRequest,
                () -> pos.post("/api/simulator/v1/transactions/field-map", request), response -> !response.containsKey("error"));
    }

    @PostMapping("/repeat")
    public SwitchLabPosExecution repeat(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
                                       @RequestParam String terminalId,
                                       @RequestParam(defaultValue = "true") boolean macEnabled,
                                       HttpServletRequest servletRequest) {
        requireAuthorized(authorization);
        if (!terminalId.matches("[A-Za-z0-9_-]{1,16}")) throw badRequest("Invalid terminal identifier");
        return execute("REPEAT", Map.of("terminalId", terminalId, "macEnabled", macEnabled),
                "Repeated transaction response received", servletRequest,
                () -> pos.post("/api/simulator/v1/transactions/repeat?terminalId=" + terminalId
                        + "&macEnabled=" + macEnabled), response -> !response.containsKey("error"));
    }

    @PostMapping("/rki")
    public SwitchLabPosExecution rki(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
                                    @RequestParam(defaultValue = "false") boolean confirm,
                                    HttpServletRequest servletRequest) {
        requireAuthorized(authorization);
        return execute("RKI", Map.of("confirmationRequested", confirm), "Key change response received",
                servletRequest, () -> pos.post("/api/simulator/v1/key-change?confirm=" + confirm),
                response -> !response.containsKey("error"));
    }

    @PostMapping("/rki/confirm")
    public SwitchLabPosExecution confirmRki(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
                                           HttpServletRequest servletRequest) {
        requireAuthorized(authorization);
        return execute("RKI_CONFIRM", Map.of(), "Key status confirmation received", servletRequest,
                () -> pos.post("/api/simulator/v1/key-change/confirm"), response -> !response.containsKey("error"));
    }

    @PostMapping("/sentinel")
    public SwitchLabPosExecution sentinel(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
                                         @RequestBody SwitchLabMtipSentinelRequest request,
                                         HttpServletRequest servletRequest) {
        requireAuthorized(authorization);
        validateSentinel(request);
        Map<String, Object> payload = sentinelPayload(request);
        Map<String, Object> safe = new LinkedHashMap<>();
        safe.put("testCase", SENTINEL);
        safe.put("pan", maskPan(request.pan()));
        safe.put("amount", request.amount());
        safe.put("terminalId", request.terminalId());
        safe.put("merchantId", request.merchantId());
        safe.put("macEnabled", !Boolean.FALSE.equals(request.macEnabled()));
        return execute(SENTINEL, safe, "MTI=0110; responseCode=00; approved=true", servletRequest,
                () -> pos.post("/api/simulator/v1/transactions/field-map", payload),
                response -> "0110".equals(response.get("responseMti"))
                        && "00".equals(response.get("responseCode"))
                        && Boolean.TRUE.equals(response.get("approved")));
    }

    private SwitchLabPosExecution execute(String operation, Map<String, Object> safeRequest,
                                          String expected, HttpServletRequest servletRequest,
                                          Operation operationCall, Verdict verdict) {
        Instant started = Instant.now();
        String correlationId = String.valueOf(servletRequest.getAttribute(SwitchLabCorrelationFilter.HEADER));
        Map<String, Object> response;
        String status;
        String result;
        try {
            response = operationCall.run();
            boolean passed = verdict.passed(response);
            status = "COMPLETED";
            result = passed ? "PASSED" : "FAILED";
        } catch (RuntimeException failure) {
            response = Map.of("error", failure.getClass().getSimpleName(), "message", safeMessage(failure.getMessage()));
            status = "FAILED";
            result = "FAILED";
        }
        Instant completed = Instant.now();
        return history.save(new SwitchLabPosExecution(UUID.randomUUID().toString(), operation, status, result,
                correlationId, started, completed, completed.toEpochMilli() - started.toEpochMilli(),
                immutableCopy(safeRequest), immutableCopy(response), expected));
    }

    private Map<String, Object> sentinelPayload(SwitchLabMtipSentinelRequest request) {
        Instant now = Instant.now();
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("2", request.pan()); fields.put("3", "000000"); fields.put("4", request.amount());
        fields.put("7", DateTimeFormatter.ofPattern("MMddHHmmss").withZone(ZoneOffset.UTC).format(now));
        fields.put("11", String.format("%06d", now.toEpochMilli() % 1_000_000));
        fields.put("12", DateTimeFormatter.ofPattern("HHmmss").withZone(ZoneOffset.UTC).format(now));
        fields.put("13", DateTimeFormatter.ofPattern("MMdd").withZone(ZoneOffset.UTC).format(now));
        fields.put("14", request.expiry()); fields.put("18", "5712"); fields.put("22", "072");
        fields.put("23", "001"); fields.put("25", "00"); fields.put("32", "022905"); fields.put("33", "022905");
        fields.put("35", request.pan() + "D" + request.expiry() + "2010123456789");
        fields.put("37", String.format("%012d", now.toEpochMilli() % 1_000_000_000_000L));
        fields.put("41", request.terminalId()); fields.put("42", request.merchantId());
        fields.put("43", "WAY POS MTIP TEST CASABLANCA MA       "); fields.put("49", "504"); fields.put("63", "007SV1.0.0");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("mti", "0100"); payload.put("fields", fields); payload.put("binaryFields", Map.of("55", EMV_DATA));
        payload.put("unsetFields", List.of()); payload.put("pin", request.pin());
        payload.put("macEnabled", !Boolean.FALSE.equals(request.macEnabled())); payload.put("validate", true);
        return payload;
    }

    private void validateSentinel(SwitchLabMtipSentinelRequest request) {
        if (request.pan() == null || !request.pan().matches("\\d{13,19}")) throw badRequest("PAN must contain 13..19 digits");
        if (request.expiry() == null || !request.expiry().matches("\\d{4}")) throw badRequest("Expiry must use YYMM format");
        if (request.pin() == null || !request.pin().matches("\\d{4,12}")) throw badRequest("PIN must contain 4..12 digits");
        if (request.amount() == null || !request.amount().matches("\\d{12}")) throw badRequest("Amount must contain 12 digits");
        if (request.terminalId() == null || !request.terminalId().matches("[A-Za-z0-9_-]{1,16}")) throw badRequest("Invalid terminal identifier");
        if (request.merchantId() == null || !request.merchantId().matches("[A-Za-z0-9_-]{1,20}")) throw badRequest("Invalid merchant identifier");
    }

    private Map<String, Object> summary(Map<String, Object> request) {
        Map<String, Object> safe = new LinkedHashMap<>();
        for (String key : List.of("mti", "processingCode", "amount", "terminalId", "merchantId", "macEnabled")) {
            if (request.containsKey(key)) safe.put(key, request.get(key));
        }
        if (request.containsKey("pan")) safe.put("pan", maskPan(String.valueOf(request.get("pan"))));
        return safe;
    }

    private Object keys(Object value) { return value instanceof Map<?, ?> map ? map.keySet() : List.of(); }
    private Map<String, Object> immutableCopy(Map<String, Object> source) { return Collections.unmodifiableMap(new LinkedHashMap<>(source)); }
    private SwitchLabPosScenarioDefinition scenario(String code, String label, String objective) { return new SwitchLabPosScenarioDefinition(code, label, objective, "WAY_POS_NATIVE", false, List.of("Scenario completed", "All steps returned by the simulator")); }
    private String maskPan(String pan) { return pan.length() < 10 ? "****" : pan.substring(0, 6) + "*".repeat(pan.length() - 10) + pan.substring(pan.length() - 4); }
    private String safeMessage(String value) { return value == null ? "POS operation failed" : value.replaceAll("\\b\\d{13,19}\\b", "[MASKED_PAN]"); }
    private ResponseStatusException badRequest(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
    private void requireAuthorized(String authorization) { if (!authentication.authorized(authorization)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid SwitchLab session"); }

    @FunctionalInterface private interface Operation { Map<String, Object> run(); }
    @FunctionalInterface private interface Verdict { boolean passed(Map<String, Object> response); }
}
