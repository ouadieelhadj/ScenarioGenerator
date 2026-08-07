package com.staging.sg.member.bff.api;

import com.staging.sg.member.bff.service.SwitchAcquiringService;
import com.staging.sg.member.bff.service.SwitchGatewayService;
import com.staging.sg.member.contracts.SwitchAcquiringOverview;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/switch/v1/acquiring")
public class SwitchAcquiringController {
    private final SwitchAcquiringService acquiring;
    private final SwitchGatewayService gateway;

    public SwitchAcquiringController(SwitchAcquiringService acquiring, SwitchGatewayService gateway) {
        this.acquiring = acquiring;
        this.gateway = gateway;
    }

    @GetMapping("/overview")
    public SwitchAcquiringOverview overview(
            @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        if (!gateway.authorized(authorization)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid Switch member session");
        }
        return acquiring.overview();
    }
}
