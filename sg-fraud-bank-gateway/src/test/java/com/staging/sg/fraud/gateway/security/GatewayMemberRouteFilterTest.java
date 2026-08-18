package com.staging.sg.fraud.gateway.security;

import com.staging.sg.fraud.gateway.domain.GatewayConnectionProfile;
import com.staging.sg.fraud.gateway.service.GatewayRouteRegistry;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import java.time.Instant;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class GatewayMemberRouteFilterTest {
    GatewayRouteRegistry routes = mock(GatewayRouteRegistry.class);
    GatewayMemberRouteFilter filter = new GatewayMemberRouteFilter(routes, true, "member_id");
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void matchingPortTokenAndSectorAreAccepted() throws Exception {
        when(routes.requireActiveRoute("REST", 8701)).thenReturn(profile("MEMBER-OUADIE", 8701));
        authenticate("MEMBER-OUADIE"); MockHttpServletRequest request = request(8701, "MONETIQUE");
        MockHttpServletResponse response = new MockHttpServletResponse(); var chain = new MockFilterChain();
        filter.doFilter(request, response, chain);
        assertThat(chain.getRequest()).isNotNull();
        assertThat(request.getAttribute(GatewayMemberRouteFilter.MEMBER_ATTRIBUTE)).isEqualTo("MEMBER-OUADIE");
        verify(routes).requireActiveSector("MEMBER-OUADIE", "MONETIQUE");
    }

    @Test void tokenFromAnotherBankIsRejectedOnOuadiePort() throws Exception {
        when(routes.requireActiveRoute("REST", 8701)).thenReturn(profile("MEMBER-OUADIE", 8701));
        authenticate("MEMBER-TRESOR"); MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request(8701, "MONETIQUE"), response, new MockFilterChain());
        assertThat(response.getStatus()).isEqualTo(403);
        verify(routes, never()).requireActiveSector(anyString(), anyString());
    }

    private MockHttpServletRequest request(int port, String sector) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/fraud-gateway/v1/events/evaluate");
        request.setLocalPort(port); request.addHeader("X-Fraud-Sector", sector); return request;
    }
    private void authenticate(String memberId) {
        Jwt jwt = new Jwt("token", Instant.now(), Instant.now().plusSeconds(60),
                java.util.Map.of("alg", "none"), java.util.Map.of("member_id", memberId));
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }
    private GatewayConnectionProfile profile(String memberId, int port) {
        return new GatewayConnectionProfile(memberId + "-REST", memberId, null, "REST", "SERVER", port,
                null, null, "REST-V1", memberId + "-CREDENTIAL", null, 30, 5, true);
    }
}
