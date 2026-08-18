package com.staging.sg.fraud.service;

import com.staging.sg.fraud.api.FraudEventRouteApi.RouteRequest;
import com.staging.sg.fraud.api.FraudEventRouteApi.RouteResponse;
import com.staging.sg.fraud.domain.FraudEventRoute;
import com.staging.sg.fraud.repository.FraudEventRouteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class FraudEventRouteService {
    private final FraudEventRouteRepository routes;
    private final FraudEventRouteResolver resolver;

    public FraudEventRouteService(FraudEventRouteRepository routes, FraudEventRouteResolver resolver) {
        this.routes = routes;
        this.resolver = resolver;
    }

    @Transactional(readOnly = true)
    public List<RouteResponse> list(String memberId) {
        return routes.findByMemberIdOrderBySectorIdAscEventTypeAsc(memberId).stream().map(this::response).toList();
    }

    @Transactional
    public RouteResponse upsert(String memberId, String sectorId, String eventType, RouteRequest request) {
        String normalizedSector = normalize("sectorId", sectorId);
        String normalizedEvent = normalize("eventType", eventType);
        resolver.validateTemplate(request.topicTemplate(), memberId, normalizedSector);
        FraudEventRoute route = routes.findByMemberIdAndSectorIdAndEventType(memberId, normalizedSector, normalizedEvent)
                .orElseGet(() -> FraudEventRoute.create(memberId, normalizedSector, normalizedEvent,
                        request.topicTemplate(), request.schemaVersion(), request.retentionClass(),
                        request.enabled(), request.priority()));
        route.update(request.topicTemplate(), request.schemaVersion(), request.retentionClass(),
                request.enabled(), request.priority());
        return response(routes.save(route));
    }

    private String normalize(String name, String value) {
        if (value == null || !value.matches("[A-Za-z0-9_-]{2,64}")) {
            throw new IllegalArgumentException(name + " must contain 2 to 64 safe characters");
        }
        return value.toUpperCase();
    }

    private RouteResponse response(FraudEventRoute route) {
        return new RouteResponse(route.id(), route.memberId(), route.sectorId(), route.eventType(),
                route.topicTemplate(), route.schemaVersion(), route.retentionClass(), route.enabled(),
                route.priority(), route.updatedAt());
    }
}
