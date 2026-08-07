package com.staging.sg.switchlab.bff.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.staging.sg.switchlab.contracts.SwitchLabComponentHealth;
import com.staging.sg.switchlab.contracts.SwitchLabEnvironmentReference;
import com.staging.sg.switchlab.contracts.SwitchLabOverview;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class SwitchLabOverviewService {
    private static final List<ComponentTarget> COMPONENTS = List.of(
            new ComponentTarget("sg-way-pos-simulator", "SWITCHLAB_WAY_POS"),
            new ComponentTarget("sg-mc-dmas-mastercard", "SWITCHLAB_MC_DMAS"),
            new ComponentTarget("sg-mc-sms-issuer", "SWITCHLAB_MC_SMS"),
            new ComponentTarget("sg-dmcs-issuer", "SWITCHLAB_DMCS"),
            new ComponentTarget("sg-swam-issuer", "SWITCHLAB_SWAM"),
            new ComponentTarget("sg-swam-lis-switch", "SWITCHLAB_SWAM_LIS"),
            new ComponentTarget("sg-visa-visanet-simulator", "SWITCHLAB_VISA_ONLINE"),
            new ComponentTarget("sg-visa-base2-network-simulator", "SWITCHLAB_VISA_BASE2"),
            new ComponentTarget("sg-merchant-site-simulator", "SWITCHLAB_MERCHANT_SITE"),
            new ComponentTarget("sg-visa-mastercard-gateway-simulator", "SWITCHLAB_CARD_GATEWAY"),
            new ComponentTarget("sg-3ds-network-simulator", "SWITCHLAB_3DS_NETWORK"));

    private final Environment properties;
    private final RestClient restClient;

    public SwitchLabOverviewService(Environment properties) {
        this.properties = properties;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(1500);
        factory.setReadTimeout(2500);
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    public List<SwitchLabEnvironmentReference> environments() {
        boolean active = Boolean.parseBoolean(property("SWITCHLAB_ENVIRONMENT_ACTIVE", "true"));
        if (!active) return List.of();
        return List.of(new SwitchLabEnvironmentReference(
                property("SWITCHLAB_ENVIRONMENT_ID", "local"),
                property("SWITCHLAB_ENVIRONMENT_CODE", "LOCAL"),
                property("SWITCHLAB_ENVIRONMENT_LABEL", "SwitchLab local"),
                property("SWITCHLAB_ENVIRONMENT_TYPE", "LOCAL"), true));
    }

    public SwitchLabOverview overview(String environmentId) {
        SwitchLabEnvironmentReference selected = environments().stream()
                .filter(item -> item.id().equals(environmentId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown or inactive SwitchLab environment"));
        List<SwitchLabComponentHealth> components = COMPONENTS.stream().map(this::probe).toList();
        long up = count(components, "UP");
        long degraded = count(components, "DEGRADED");
        long down = count(components, "DOWN");
        String overall = down > 0 ? "DOWN" : degraded > 0 ? "DEGRADED"
                : up == components.size() ? "UP" : "UNKNOWN";
        return new SwitchLabOverview("1.0", selected, overall, up, degraded, down,
                components, Instant.now(), UUID.randomUUID().toString());
    }

    public SwitchLabComponentHealth probeComponent(String componentCode) {
        ComponentTarget target = COMPONENTS.stream()
                .filter(item -> item.code().equals(componentCode))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown SwitchLab component"));
        return probe(target);
    }

    private SwitchLabComponentHealth probe(ComponentTarget target) {
        Instant checkedAt = Instant.now();
        String healthUrl = property(target.propertyPrefix() + "_HEALTH_URL", "");
        if (healthUrl.isBlank()) {
            return new SwitchLabComponentHealth(target.code(), "UNKNOWN", checkedAt, List.of(), List.of());
        }
        try {
            ResponseEntity<JsonNode> health = restClient.get().uri(healthUrl).retrieve().toEntity(JsonNode.class);
            String status = health.getStatusCode().is2xxSuccessful() ? statusFrom(health.getBody()) : "DOWN";
            return new SwitchLabComponentHealth(target.code(), status, checkedAt, capabilities(target), List.of());
        } catch (RuntimeException unavailable) {
            return new SwitchLabComponentHealth(target.code(), "DOWN", checkedAt, List.of(), List.of());
        }
    }

    private List<String> capabilities(ComponentTarget target) {
        String url = property(target.propertyPrefix() + "_CAPABILITIES_URL", "");
        if (url.isBlank()) return List.of();
        try {
            JsonNode body = restClient.get().uri(url).retrieve().body(JsonNode.class);
            JsonNode values = body != null && body.has("capabilities") ? body.get("capabilities") : body;
            if (values == null || !values.isArray()) return List.of();
            List<String> result = new ArrayList<>();
            values.forEach(value -> result.add(value.isTextual() ? value.asText() : value.toString()));
            return List.copyOf(result);
        } catch (RuntimeException unavailable) {
            return List.of();
        }
    }

    private String statusFrom(JsonNode body) {
        if (body == null) return "UP";
        String value = body.has("status") ? body.get("status").asText("UP") : "UP";
        return switch (value.toUpperCase(Locale.ROOT)) {
            case "UP", "OK", "READY", "CONNECTED" -> "UP";
            case "DEGRADED", "WARNING" -> "DEGRADED";
            default -> "DOWN";
        };
    }

    private long count(List<SwitchLabComponentHealth> values, String status) {
        return values.stream().filter(item -> status.equals(item.status())).count();
    }

    private String property(String name, String fallback) {
        return properties.getProperty(name, fallback);
    }

    private record ComponentTarget(String code, String propertyPrefix) { }
}
