package com.staging.sg.switchlab.bff.api;

import com.staging.sg.switchlab.bff.service.SwitchLabGatewayService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import java.nio.charset.StandardCharsets;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SwitchLabGatewayController {
    private final SwitchLabGatewayService gateway;

    public SwitchLabGatewayController(SwitchLabGatewayService gateway) {
        this.gateway = gateway;
    }

    @RequestMapping(path = {
            "/auth/login",
            "/api/me/navigation",
            "/api/admin/users", "/api/admin/users/**",
            "/api/admin/roles", "/api/admin/roles/**",
            "/api/admin/deployments", "/api/admin/deployments/**",
            "/api/campaigns", "/api/campaigns/**",
            "/api/executions", "/api/executions/**",
            "/api/admin/tests", "/api/admin/tests/**", "/api/tests/my",
            "/api/networks", "/api/networks/**",
            "/api/admin/message-types", "/api/admin/message-types/**"
    })
    public ResponseEntity<byte[]> forward(HttpServletRequest request,
                                          @RequestHeader HttpHeaders headers,
                                          @RequestBody(required = false) byte[] body) {
        if (!"/auth/login".equals(request.getRequestURI())
                && !gateway.authorized(headers.getFirst(HttpHeaders.AUTHORIZATION))) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new byte[0]);
        }
        String path = request.getRequestURI();
        if (path.matches("/api/campaigns/\\d+/run")
                || path.matches("/api/executions/(start|loadtest)/.*")) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body("Member execution engines are not authorized in SwitchLab"
                            .getBytes(StandardCharsets.UTF_8));
        }
        return gateway.forward(path, request.getQueryString(),
                HttpMethod.valueOf(request.getMethod()), headers, body);
    }
}
