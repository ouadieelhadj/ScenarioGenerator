package com.staging.sg.member.bff.api;

import com.staging.sg.member.bff.service.SwitchFraudService;
import com.staging.sg.member.bff.service.SwitchGatewayService;
import com.staging.sg.member.contracts.SwitchFraudOverview;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/switch/v1/fraud")
public class SwitchFraudController {
    private final SwitchFraudService fraud;
    private final SwitchGatewayService gateway;

    public SwitchFraudController(SwitchFraudService fraud, SwitchGatewayService gateway) {
        this.fraud = fraud;
        this.gateway = gateway;
    }

    @GetMapping("/overview")
    public SwitchFraudOverview overview(
            @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        if (!gateway.authorized(authorization)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid Switch member session");
        }
        return fraud.overview();
    }

    @RequestMapping(value = "/platform/**")
    public ResponseEntity<byte[]> proxyPlatform(HttpServletRequest request,
            @RequestHeader HttpHeaders headers,
            @RequestBody(required = false) byte[] body) {
        String authorization = headers.getFirst(HttpHeaders.AUTHORIZATION);
        if (!gateway.authorized(authorization)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid product session");
        String path = request.getRequestURI().substring("/api/switch/v1/fraud/platform".length());
        return fraud.forward("/api/fraud/v1" + path, request.getQueryString(), HttpMethod.valueOf(request.getMethod()), headers, body);
    }}
