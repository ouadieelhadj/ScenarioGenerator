package com.staging.sg.member.bff.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.staging.sg.member.contracts.SwitchAcquiringFeature;
import com.staging.sg.member.contracts.SwitchAcquiringOverview;
import com.staging.sg.member.contracts.SwitchMemberServiceStatus;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class SwitchAcquiringService {
    private final String acquiringBaseUrl;
    private final String wayPosBaseUrl;
    private final RestClient client;

    public SwitchAcquiringService(
            @Value("${switch.member.acquiring-base-url:}") String acquiringBaseUrl,
            @Value("${switch.member.way-pos-base-url:}") String wayPosBaseUrl) {
        this.acquiringBaseUrl = normalize(acquiringBaseUrl);
        this.wayPosBaseUrl = normalize(wayPosBaseUrl);
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(1500);
        factory.setReadTimeout(2500);
        this.client = RestClient.builder().requestFactory(factory).build();
    }

    public SwitchAcquiringOverview overview() {
        Probe acquiring = probe(acquiringBaseUrl, "/api/acquiring/v1/health",
                "/api/acquiring/v1/capabilities");
        Probe wayPos = probe(wayPosBaseUrl, "/api/routing/v1/health",
                "/api/routing/v1/capabilities");
        List<SwitchMemberServiceStatus> services = List.of(
                service("SG_ACQUIRING", "Acquisition membre", acquiring),
                service("SG_WAY_POS_SERVER", "ServerPOS membre", wayPos));

        boolean acquiringUp = "UP".equals(acquiring.status());
        boolean wayPosUp = "UP".equals(wayPos.status());
        boolean threeDs = acquiring.booleanCapability("threeDS");
        List<SwitchAcquiringFeature> features = List.of(
                blockedCatalog("ACCEPTANCE_PRODUCTS", "Produits d'acceptation", acquiringUp, true),
                blockedCatalog("MERCHANTS_OUTLETS", "Commerçants et points de vente", acquiringUp, true),
                blockedCatalog("MERCHANT_CONTRACTS", "Contrats commerçants", acquiringUp, true),
                blockedCatalog("TERMINALS_ASSIGNMENTS", "Terminaux et affectations", acquiringUp || wayPosUp, true),
                blockedCatalog("ECOMMERCE_STORES", "Boutiques e-commerce", acquiringUp, true),
                blockedCatalog("ACCEPTANCE_ROUTING", "Profils d'acceptation et routage", acquiringUp || wayPosUp, true),
                blockedTransaction(acquiringUp),
                new SwitchAcquiringFeature("THREE_DS_EVIDENCE", "Authentifications 3DS et preuves",
                        threeDs ? "BLOCKED" : "UNAVAILABLE", threeDs, false, false, false,
                        threeDs
                                ? "Le backend ne fournit pas encore de catalogue de preuves 3DS consultable."
                                : "sg-acquiring annonce explicitement threeDS=false."),
                new SwitchAcquiringFeature("DEPENDENCIES_EVENTS_AUDIT", "Dépendances, événements et audit",
                        acquiringUp || wayPosUp ? "BLOCKED" : "UNAVAILABLE", acquiringUp || wayPosUp,
                        false, false, false,
                        "Aucune API membre de consultation des événements et de l'audit Acquisition n'est exposée."));

        String overall = services.stream().allMatch(item -> "UP".equals(item.status())) ? "UP"
                : services.stream().anyMatch(item -> "UP".equals(item.status())) ? "DEGRADED" : "UNKNOWN";
        return new SwitchAcquiringOverview("1.0", overall, services, features,
                Instant.now(), UUID.randomUUID().toString());
    }

    private SwitchAcquiringFeature blockedCatalog(String code, String label,
            boolean endpointAvailable, boolean makerChecker) {
        return new SwitchAcquiringFeature(code, label,
                endpointAvailable ? "BLOCKED" : "UNAVAILABLE", endpointAvailable,
                false, false, makerChecker,
                endpointAvailable
                        ? "Les commandes backend existent, mais les API GET de catalogue et la délégation d'identité sécurisée sont absentes."
                        : "Le service membre requis n'est pas configuré ou disponible.");
    }

    private SwitchAcquiringFeature blockedTransaction(boolean acquiringUp) {
        return new SwitchAcquiringFeature("POS_ECOMMERCE_TRANSACTIONS", "Transactions POS et e-commerce",
                acquiringUp ? "BLOCKED" : "UNAVAILABLE", acquiringUp, false, false, false,
                acquiringUp
                        ? "Le frontend ne transmet pas de PAN : un résolveur serveur de référence carte est requis."
                        : "Le service Acquisition membre n'est pas configuré ou disponible.");
    }

    private SwitchMemberServiceStatus service(String code, String label, Probe probe) {
        return new SwitchMemberServiceStatus(code, label, probe.configured(), probe.status(),
                probe.capabilities(), probe.limitation());
    }

    private Probe probe(String baseUrl, String healthPath, String capabilitiesPath) {
        if (baseUrl.isBlank()) {
            return new Probe(false, "UNKNOWN", List.of(), "URL membre non configurée.", null);
        }
        try {
            JsonNode health = client.get().uri(baseUrl + healthPath).retrieve().body(JsonNode.class);
            String status = normalizeStatus(health);
            JsonNode capabilities = client.get().uri(baseUrl + capabilitiesPath).retrieve().body(JsonNode.class);
            return new Probe(true, status, flattenCapabilities(capabilities), null, capabilities);
        } catch (RuntimeException unavailable) {
            return new Probe(true, "DOWN", List.of(), "Service membre inaccessible.", null);
        }
    }

    private List<String> flattenCapabilities(JsonNode root) {
        if (root == null || !root.isObject()) return List.of();
        List<String> values = new ArrayList<>();
        root.fields().forEachRemaining(entry -> {
            JsonNode value = entry.getValue();
            if (value.isBoolean() || value.isTextual() || value.isNumber()) {
                values.add(entry.getKey() + "=" + value.asText());
            } else if (value.isArray()) {
                List<String> items = new ArrayList<>();
                value.forEach(item -> items.add(item.asText()));
                values.add(entry.getKey() + "=" + String.join(",", items));
            }
        });
        return List.copyOf(values);
    }

    private String normalizeStatus(JsonNode body) {
        String value = body != null && body.has("status") ? body.get("status").asText() : "UP";
        return switch (value.toUpperCase(Locale.ROOT)) {
            case "UP", "OK", "READY", "CONNECTED" -> "UP";
            case "DEGRADED", "WARNING" -> "DEGRADED";
            default -> "DOWN";
        };
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().replaceAll("/+$", "");
    }

    private record Probe(boolean configured, String status, List<String> capabilities,
                         String limitation, JsonNode rawCapabilities) {
        boolean booleanCapability(String name) {
            return rawCapabilities != null && rawCapabilities.path(name).asBoolean(false);
        }
    }
}
