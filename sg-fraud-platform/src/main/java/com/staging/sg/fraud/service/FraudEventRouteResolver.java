package com.staging.sg.fraud.service;

import com.staging.sg.fraud.domain.FraudEventOutbox;
import com.staging.sg.fraud.domain.FraudEventRoute;
import com.staging.sg.fraud.repository.FraudEventRouteRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.regex.Pattern;

@Service
public class FraudEventRouteResolver {
    private static final Pattern TOPIC = Pattern.compile("[A-Za-z0-9._-]{1,249}");
    private final FraudEventRouteRepository routes;
    private final String fallbackTemplate;
    private final boolean dynamicRoutingRequired;

    public FraudEventRouteResolver(FraudEventRouteRepository routes,
            @Value("${fraud.integrations.event-stream.topic:fraud.{memberId}.{sectorId}.risk-assessment-completed.v1}") String fallbackTemplate,
            @Value("${fraud.integrations.event-stream.dynamic-routing-required:false}") boolean dynamicRoutingRequired) {
        this.routes = routes;
        this.fallbackTemplate = fallbackTemplate;
        this.dynamicRoutingRequired = dynamicRoutingRequired;
    }

    public RouteDecision resolve(FraudEventOutbox event) {
        FraudEventRoute route = routes.findByMemberIdAndSectorIdAndEventType(
                event.memberId(), event.sectorId(), event.eventType()).orElse(null);
        if (route != null && route.enabled()) {
            return decision(route.topicTemplate(), event, route.schemaVersion(), route.retentionClass(), route.priority(), "DATABASE");
        }
        if (dynamicRoutingRequired) {
            throw new IllegalStateException("No enabled event route for member, sector and event type");
        }
        return decision(fallbackTemplate, event, "v1", "STANDARD", 100, "FALLBACK");
    }

    public void validateTemplate(String template, String memberId, String sectorId) {
        resolveAndValidate(template, memberId, sectorId);
    }

    private RouteDecision decision(String template, FraudEventOutbox event, String schemaVersion,
            String retentionClass, int priority, String source) {
        return new RouteDecision(resolveAndValidate(template, event.memberId(), event.sectorId()),
                schemaVersion, retentionClass, priority, source);
    }

    private String resolveAndValidate(String template, String memberId, String sectorId) {
        String safeMember = safe(memberId);
        String safeSector = safe(sectorId);
        String topic = template.replace("{memberId}", safeMember).replace("{sectorId}", safeSector);
        String requiredPrefix = "fraud." + safeMember + "." + safeSector + ".";
        if (!TOPIC.matcher(topic).matches() || topic.contains("{") || !topic.startsWith(requiredPrefix)) {
            throw new IllegalArgumentException("Kafka topic must be valid and isolated by member and sector");
        }
        return topic;
    }

    private String safe(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "-");
    }

    public record RouteDecision(String topic, String schemaVersion, String retentionClass, int priority, String source) {}
}
