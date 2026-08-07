package com.staging.sg.switchlab.bff.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.staging.sg.switchlab.contracts.SwitchLabOnlineNetwork;
import com.staging.sg.switchlab.contracts.SwitchLabOnlineKeyStatus;
import com.staging.sg.switchlab.contracts.SwitchLabOnlineScenario;
import com.staging.sg.switchlab.contracts.SwitchLabOnlineScenarioResult;
import com.staging.sg.switchlab.contracts.SwitchLabOnlineSession;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class SwitchLabOnlineService {
    private final RestClient client;
    private final String dmasUrl;
    private final String visaUrl;
    private final String swamUrl;

    public SwitchLabOnlineService(@Value("${switchlab.online.dmas-base-url:}") String dmasUrl,
                                  @Value("${switchlab.online.visa-base-url:}") String visaUrl,
                                  @Value("${switchlab.online.swam-base-url:}") String swamUrl) {
        this.dmasUrl = clean(dmasUrl); this.visaUrl = clean(visaUrl); this.swamUrl = clean(swamUrl);
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(1500); factory.setReadTimeout(3000);
        this.client = RestClient.builder().requestFactory(factory).build();
    }

    public List<SwitchLabOnlineNetwork> networks() {
        return List.of(
                network("MASTERCARD_DMAS", "Mastercard DMAS", "sg-mc-dmas-mastercard", dmasUrl,
                        true, true, false, List.of("Financial network-to-member API absent")),
                new SwitchLabOnlineNetwork("MASTERCARD_SMS", "Mastercard SMS", "sg-mc-sms-issuer", "BLOCKED",
                        false, false, false, List.of("Existing API exposes clear key material", "Online command API absent")),
                network("VISA_ONLINE", "VisaNet Online", "sg-visa-visanet-simulator", visaUrl,
                        false, false, false, List.of("No session API", "Raw ISO envelope forbidden from browser")),
                network("SWAM_ONLINE", "SWAM Online", "sg-swam-issuer", swamUrl,
                        true, false, false, List.of("Secure transaction reference resolver absent")));
    }

    public SwitchLabOnlineSession session(String networkCode) {
        return switch (networkCode.toUpperCase(Locale.ROOT)) {
            case "MASTERCARD_DMAS" -> dmasSession();
            case "VISA_ONLINE" -> visaSession();
            case "SWAM_ONLINE" -> swamSession();
            case "MASTERCARD_SMS" -> unavailable(networkCode, "BLOCKED");
            default -> throw new IllegalArgumentException("Unknown SwitchLab Online network");
        };
    }

    public SwitchLabOnlineKeyStatus keyStatus(String networkCode) {
        if (!"MASTERCARD_DMAS".equalsIgnoreCase(networkCode))
            return new SwitchLabOnlineKeyStatus(networkCode, "UNKNOWN", "UNAVAILABLE", null, null,
                    "Sanitized key status API absent", Instant.now());
        JsonNode body = get(dmasUrl, "/api/admin/dmas/keys/current");
        return new SwitchLabOnlineKeyStatus("MASTERCARD_DMAS", text(body, "key_type", "PEK"),
                text(body, "status", "NONE"), text(body, "kcv", null),
                "hsm://dmas/" + text(body, "member_group_id", "default") + "/pek", null, Instant.now());
    }

    public List<SwitchLabOnlineScenario> scenarios() {
        return List.of(
                new SwitchLabOnlineScenario("DMAS.NETWORK.ECHO", "MASTERCARD_DMAS", "Network echo 0800/0810", "CONNECTIVITY", true, null),
                blocked("DMAS.AUTH.NOMINAL", "MASTERCARD_DMAS", "Authorization nominal", "Financial push API absent"),
                blocked("DMAS.AUTH.REFUSAL", "MASTERCARD_DMAS", "Authorization refusal", "Financial push API absent"),
                blocked("SMS.AUTH.NOMINAL", "MASTERCARD_SMS", "Authorization nominal", "Online command API absent"),
                blocked("VISA.AUTH.NOMINAL", "VISA_ONLINE", "Authorization nominal", "Server-side ISO builder absent"),
                blocked("VISA.AUTH.REFUSAL", "VISA_ONLINE", "Authorization refusal", "Server-side ISO builder absent"),
                blocked("SWAM.AUTH.NOMINAL", "SWAM_ONLINE", "Authorization nominal", "Secure PAN reference resolver absent"),
                blocked("SWAM.AUTH.REFUSAL", "SWAM_ONLINE", "Authorization refusal", "Secure PAN reference resolver absent"));
    }

    public SwitchLabOnlineScenarioResult execute(String scenarioCode, String correlationId) {
        if (!"DMAS.NETWORK.ECHO".equals(scenarioCode)) throw new IllegalStateException("Online scenario is not executable");
        JsonNode body = client.post().uri(dmasUrl + "/api/admin/dmas/jpos/push/network?de70=270&wait=true")
                .retrieve().body(JsonNode.class);
        boolean success = body != null && body.path("success").asBoolean(false);
        return new SwitchLabOnlineScenarioResult(UUID.randomUUID().toString(), scenarioCode, "MASTERCARD_DMAS",
                success ? "COMPLETED" : "FAILED", text(body, "de039", null), success, correlationId, Instant.now());
    }

    private SwitchLabOnlineSession dmasSession() {
        JsonNode body = get(dmasUrl, "/api/admin/dmas/jpos/status");
        return new SwitchLabOnlineSession("MASTERCARD_DMAS", text(body, "status", "UNKNOWN"),
                text(body, "role", "SERVER"), "PERMANENT", text(body, "interface", null),
                text(body, "bank_code", null), body.path("session_active").asBoolean(false), Instant.now());
    }
    private SwitchLabOnlineSession visaSession() {
        JsonNode body = get(visaUrl, "/api/visa/network/v1/health");
        boolean up = "UP".equalsIgnoreCase(text(body, "status", "DOWN"));
        return new SwitchLabOnlineSession("VISA_ONLINE", up ? "UP" : "DOWN", "NETWORK_SIMULATOR",
                "REQUEST_RESPONSE", null, null, up, Instant.now());
    }
    private SwitchLabOnlineSession swamSession() {
        JsonNode body = get(swamUrl, "/api/admin/swam/connection");
        boolean connected = body.path("connected").asBoolean(false);
        return new SwitchLabOnlineSession("SWAM_ONLINE", connected ? "CONNECTED" : "DISCONNECTED",
                "SERVER", text(body, "mode", "PERMANENT"), null, null, connected, Instant.now());
    }
    private SwitchLabOnlineSession unavailable(String networkCode, String status) {
        return new SwitchLabOnlineSession(networkCode, status, "NETWORK_SIMULATOR", "UNAVAILABLE",
                null, null, false, Instant.now());
    }
    private SwitchLabOnlineNetwork network(String code, String label, String module, String baseUrl,
                                           boolean sessions, boolean keys, boolean transactions, List<String> limits) {
        return new SwitchLabOnlineNetwork(code, label, module, baseUrl.isBlank() ? "UNCONFIGURED" : "CONFIGURED",
                sessions, keys, transactions, limits);
    }
    private SwitchLabOnlineScenario blocked(String code, String network, String label, String limitation) {
        return new SwitchLabOnlineScenario(code, network, label, code.endsWith("REFUSAL") ? "REFUSAL" : "NOMINAL", false, limitation);
    }
    private JsonNode get(String baseUrl, String path) {
        if (baseUrl.isBlank()) throw new IllegalStateException("SwitchLab Online adapter is not configured");
        return client.get().uri(baseUrl + path).retrieve().body(JsonNode.class);
    }
    private String text(JsonNode body, String field, String fallback) {
        return body != null && body.hasNonNull(field) ? body.get(field).asText() : fallback;
    }
    private static String clean(String value) {
        if (value == null || value.isBlank()) return "";
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
