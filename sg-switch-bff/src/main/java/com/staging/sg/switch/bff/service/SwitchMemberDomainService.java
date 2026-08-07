package com.staging.sg.member.bff.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.staging.sg.member.contracts.SwitchDomainFeature;
import com.staging.sg.member.contracts.SwitchDomainOverview;
import com.staging.sg.member.contracts.SwitchMemberServiceStatus;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.core.env.Environment;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class SwitchMemberDomainService {
    private static final Map<String, Target> TARGETS = Map.ofEntries(
            Map.entry("ISSUING", new Target("SG_CARD_ISSUING", "Issuing membre", "switch.member.issuing-base-url", "/api/issuing/v1/health", "/api/issuing/v1/capabilities")),
            Map.entry("DMAS", new Target("SG_MC_DMAS_MEMBER", "Mastercard DMAS membre", "switch.member.dmas-base-url", "/api/routing/v1/health", "/api/routing/v1/capabilities")),
            Map.entry("SMS", new Target("SG_MC_SMS_ACQUIRER", "Mastercard SMS acquéreur", "switch.member.sms-base-url", "/api/routing/v1/health", "/api/routing/v1/capabilities")),
            Map.entry("SWAM", new Target("SG_SWAM_ACQUIRER", "SWAM acquéreur", "switch.member.swam-base-url", "/api/routing/v1/health", "/api/routing/v1/capabilities")),
            Map.entry("VISA_ONLINE", new Target("SG_VISA_ONLINE_MEMBER", "Visa Online membre", "switch.member.visa-online-base-url", "/api/visa/online/v1/health", null)),
            Map.entry("DMCS", new Target("SG_DMCS_ACQUIRER", "Mastercard Clearing acquéreur", "switch.member.dmcs-base-url", "/api/dmcs/status", null)),
            Map.entry("SWAM_LIS", new Target("SG_SWAM_LIS_MEMBER", "SWAM LIS membre", "switch.member.swam-lis-base-url", "/api/clearing/health", null)),
            Map.entry("VISA_BASE2", new Target("SG_VISA_BASE2_MEMBER", "Visa Base II membre", "switch.member.visa-base2-base-url", "/api/visa/base2/v1/health", null)),
            Map.entry("THREE_DS", new Target("SG_3DS_MEMBER", "ACS et 3DS Server membre", "switch.member.three-ds-base-url", "/api/3ds/member/v1/health", null)));

    private final Environment environment;
    private final RestClient client;

    public SwitchMemberDomainService(Environment environment) {
        this.environment = environment;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(1500);
        factory.setReadTimeout(2500);
        this.client = RestClient.builder().requestFactory(factory).build();
    }

    public SwitchDomainOverview overview(String requestedDomain) {
        String domain = requestedDomain.toUpperCase(Locale.ROOT);
        List<String> codes = switch (domain) {
            case "ISSUING" -> List.of("ISSUING");
            case "NETWORKS" -> List.of("DMAS", "SMS", "SWAM", "VISA_ONLINE");
            case "CLEARING" -> List.of("DMCS", "SWAM_LIS", "VISA_BASE2");
            case "ECOMMERCE" -> List.of("THREE_DS", "VISA_ONLINE");
            case "INDUSTRIALIZATION" -> List.of("ISSUING", "DMAS", "SMS", "SWAM", "VISA_ONLINE",
                    "DMCS", "SWAM_LIS", "VISA_BASE2", "THREE_DS");
            default -> throw new IllegalArgumentException("Unknown Switch member domain");
        };
        List<SwitchMemberServiceStatus> services = codes.stream().map(this::probe).toList();
        String overall = overall(services);
        return new SwitchDomainOverview("1.0", domain, overall, services, features(domain),
                Instant.now(), UUID.randomUUID().toString());
    }

    private List<SwitchDomainFeature> features(String domain) {
        return switch (domain) {
            case "ISSUING" -> List.of(
                    blocked("ISSUING_PRODUCTS", "Produits cartes", true, true, "Les commandes existent, mais aucun catalogue GET produit n'est exposé."),
                    blocked("ISSUING_CONTRACTS", "Contrats porteurs", true, true, "Aucune API de consultation des contrats n'est exposée."),
                    blocked("CARDS", "Cartes virtuelles et physiques", true, true, "Aucune API de consultation assainie des cartes n'est exposée."),
                    blocked("ISSUING_INTERFACES", "Interfaces issuing", true, true, "Le registre transverse membre et ses lectures sont absents."),
                    blocked("ISSUING_AUTHORIZATIONS", "Autorisations et pré-clearing", true, false, "Le frontend exige une référence carte serveur ; aucun PAN n'est accepté dans le navigateur."));
            case "NETWORKS" -> List.of(
                    blocked("NETWORK_SESSIONS", "Sessions DMAS, SMS, SWAM et Visa", true, false, "Les états ne disposent pas encore d'un contrat membre unifié et assaini."),
                    blocked("NETWORK_KEYS", "État des clés réseau", true, true, "Les endpoints techniques ne doivent pas relayer de clé ; une vue KCV/référence HSM dédiée manque."),
                    blocked("REALTIME_ROUTING", "Routage temps réel", true, true, "Les routes transactionnelles existent, mais aucun résolveur serveur de référence carte n'est disponible."),
                    blocked("NETWORK_TRANSACTIONS", "Journal transactionnel réseau", false, false, "Aucune API membre consolidée du journal transactionnel n'est exposée."));
            case "CLEARING" -> List.of(
                    blocked("CLEARING_FILES", "Fichiers DMCS, SWAM LIS et Visa Base II", true, false, "Les catalogues existent seulement pour certains réseaux et ne partagent pas encore un contrat assaini."),
                    blocked("CLEARING_EOD", "Clôture EOD", true, true, "Les commandes EOD ne sont pas raccordées au Maker/Checker transverse."),
                    blocked("RECONCILIATION", "Rapprochement", false, true, "Aucun moteur de rapprochement membre consolidé n'est exposé."),
                    blocked("SETTLEMENT", "Settlement", false, true, "Aucune API de calcul, validation et comptabilisation du settlement n'est exposée."),
                    blocked("DISPUTES", "Litiges et chargebacks", true, true, "Des commandes réseau existent, mais les listes, timelines et décisions Maker/Checker manquent."));
            case "ECOMMERCE" -> List.of(
                    blocked("THREE_DS_AUTHENTICATIONS", "Authentifications 3DS", true, false, "La consultation existe uniquement par identifiant ; aucune liste globale n'est disponible."),
                    blocked("THREE_DS_PROOFS", "Preuves ACS et 3DS Server", false, false, "Aucun registre de preuves 3DS consultable n'est exposé."),
                    blocked("ECOMMERCE_AUTHORIZATIONS", "Autorisations e-commerce", true, false, "Un résolveur serveur de référence carte est requis ; aucun PAN n'est saisi dans le frontend."));
            case "INDUSTRIALIZATION" -> List.of(
                    available("DEPLOYMENTS", "Déploiements et licences", "Les écrans communs sont servis par le BFF Switch séparé."),
                    blocked("OBSERVABILITY", "Santé et observabilité", true, false, "La santé est agrégée, mais les métriques et alertes durables ne sont pas exposées."),
                    blocked("AUDIT", "Audit produit", false, false, "Aucun journal d'audit membre consolidé n'est exposé."),
                    blocked("BACKUP_RESTORE", "Sauvegarde et restauration", false, true, "Aucune API de sauvegarde/restauration contrôlée par Maker/Checker n'est exposée."));
            default -> List.of();
        };
    }

    private SwitchDomainFeature blocked(String code, String label, boolean endpoint,
            boolean makerChecker, String limitation) {
        return new SwitchDomainFeature(code, label, endpoint ? "BLOCKED" : "UNAVAILABLE",
                endpoint, false, false, makerChecker, limitation);
    }

    private SwitchDomainFeature available(String code, String label, String limitation) {
        return new SwitchDomainFeature(code, label, "AVAILABLE", true,
                true, true, true, limitation);
    }

    private SwitchMemberServiceStatus probe(String targetCode) {
        Target target = TARGETS.get(targetCode);
        String baseUrl = normalize(environment.getProperty(target.environmentName(), ""));
        if (baseUrl.isBlank()) {
            return new SwitchMemberServiceStatus(target.code(), target.label(), false,
                    "UNKNOWN", List.of(), "URL membre non configurée.");
        }
        try {
            JsonNode health = client.get().uri(baseUrl + target.healthPath()).retrieve().body(JsonNode.class);
            List<String> capabilities = target.capabilitiesPath() == null ? List.of()
                    : flatten(client.get().uri(baseUrl + target.capabilitiesPath()).retrieve().body(JsonNode.class));
            return new SwitchMemberServiceStatus(target.code(), target.label(), true,
                    normalizeStatus(health), capabilities, null);
        } catch (RuntimeException unavailable) {
            return new SwitchMemberServiceStatus(target.code(), target.label(), true,
                    "DOWN", List.of(), "Service membre inaccessible.");
        }
    }

    private String overall(List<SwitchMemberServiceStatus> services) {
        if (services.stream().allMatch(item -> "UP".equals(item.status()))) return "UP";
        if (services.stream().anyMatch(item -> "UP".equals(item.status()))) return "DEGRADED";
        if (services.stream().anyMatch(SwitchMemberServiceStatus::configured)) return "DOWN";
        return "UNKNOWN";
    }

    private List<String> flatten(JsonNode root) {
        if (root == null || !root.isObject()) return List.of();
        List<String> values = new ArrayList<>();
        root.fields().forEachRemaining(entry -> {
            JsonNode value = entry.getValue();
            if (value.isBoolean() || value.isTextual() || value.isNumber()) values.add(entry.getKey() + "=" + value.asText());
            else if (value.isArray()) {
                List<String> items = new ArrayList<>(); value.forEach(item -> items.add(item.asText()));
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

    private String normalize(String value) { return value == null ? "" : value.trim().replaceAll("/+$", ""); }

    private record Target(String code, String label, String environmentName,
                          String healthPath, String capabilitiesPath) { }
}
