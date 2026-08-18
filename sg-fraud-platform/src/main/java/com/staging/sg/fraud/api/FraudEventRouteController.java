package com.staging.sg.fraud.api;

import com.staging.sg.fraud.api.FraudEventRouteApi.RouteRequest;
import com.staging.sg.fraud.api.FraudEventRouteApi.RouteResponse;
import com.staging.sg.fraud.security.MemberContext;
import com.staging.sg.fraud.service.FraudEventRouteService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/fraud/v1/admin/event-routes")
public class FraudEventRouteController {
    private final FraudEventRouteService routes;
    private final MemberContext members;

    public FraudEventRouteController(FraudEventRouteService routes, MemberContext members) {
        this.routes = routes;
        this.members = members;
    }

    @GetMapping
    public List<RouteResponse> list(Authentication authentication) {
        return routes.list(members.requireMemberId(authentication));
    }

    @PutMapping("/{sectorId}/{eventType}")
    public RouteResponse upsert(Authentication authentication, @PathVariable String sectorId,
            @PathVariable String eventType, @Valid @RequestBody RouteRequest request) {
        return routes.upsert(members.requireMemberId(authentication), sectorId, eventType, request);
    }
}
