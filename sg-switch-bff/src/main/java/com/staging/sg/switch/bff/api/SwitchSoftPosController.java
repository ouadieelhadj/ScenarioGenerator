package com.staging.sg.member.bff.api;

import com.staging.sg.member.bff.service.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/switch/v1/softpos")
public class SwitchSoftPosController {
    private final SwitchSoftPosService softpos; private final SwitchGatewayService gateway;
    public SwitchSoftPosController(SwitchSoftPosService softpos, SwitchGatewayService gateway) { this.softpos = softpos; this.gateway = gateway; }
    @RequestMapping("/**")
    public ResponseEntity<byte[]> proxy(HttpServletRequest request, @RequestHeader HttpHeaders headers,
            @RequestBody(required = false) byte[] body) {
        if (!gateway.authorized(headers.getFirst(HttpHeaders.AUTHORIZATION))) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid Switch member session");
        String path = request.getRequestURI().substring("/api/switch/v1/softpos".length());
        return softpos.forward("/api/admin/softpos/v1" + path, request.getQueryString(), HttpMethod.valueOf(request.getMethod()), headers, body);
    }
}
