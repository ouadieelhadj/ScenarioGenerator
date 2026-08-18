package com.staging.sg.fraud.gateway.security;

import com.staging.sg.fraud.gateway.domain.GatewayConnectionProfile;
import com.staging.sg.fraud.gateway.service.GatewayRouteRegistry;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

@Component
public class GatewayMemberRouteFilter extends OncePerRequestFilter {
    public static final String MEMBER_ATTRIBUTE = "fraud.gateway.memberId";
    public static final String SECTOR_ATTRIBUTE = "fraud.gateway.sectorId";
    private final GatewayRouteRegistry routes; private final boolean enforced; private final String memberClaim;
    public GatewayMemberRouteFilter(GatewayRouteRegistry routes,
            @Value("${fraud-gateway.routing.enforce-dedicated-rest-ports:false}") boolean enforced,
            @Value("${fraud-gateway.security.member-claim:member_id}") String memberClaim) {
        this.routes = routes; this.enforced = enforced; this.memberClaim = memberClaim;
    }
    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        return !enforced || !request.getRequestURI().startsWith("/api/fraud-gateway/v1/")
                || request.getRequestURI().endsWith("/health");
    }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        try {
            GatewayConnectionProfile route = routes.requireActiveRoute(GatewayRouteRegistry.REST, request.getLocalPort());
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (!(authentication instanceof JwtAuthenticationToken jwt)
                    || !route.memberId().equals(jwt.getToken().getClaimAsString(memberClaim))) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "Authenticated member does not match gateway port"); return;
            }
            String sectorId = request.getHeader("X-Fraud-Sector");
            if (sectorId == null || sectorId.isBlank()) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "X-Fraud-Sector is required"); return;
            }
            routes.requireActiveSector(route.memberId(), sectorId);
            request.setAttribute(MEMBER_ATTRIBUTE, route.memberId()); request.setAttribute(SECTOR_ATTRIBUTE, sectorId);
            chain.doFilter(request, response);
        } catch (GatewayRouteRegistry.UnknownGatewayRouteException
                | GatewayRouteRegistry.InactiveGatewayRouteException failure) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, failure.getMessage());
        }
    }
}
