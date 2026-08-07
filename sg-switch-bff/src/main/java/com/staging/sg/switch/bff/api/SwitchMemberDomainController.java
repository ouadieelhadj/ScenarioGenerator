package com.staging.sg.member.bff.api;

import com.staging.sg.member.bff.service.SwitchGatewayService;
import com.staging.sg.member.bff.service.SwitchMemberDomainService;
import com.staging.sg.member.contracts.SwitchDomainOverview;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/switch/v1/domains")
public class SwitchMemberDomainController {
    private final SwitchMemberDomainService domains;
    private final SwitchGatewayService gateway;

    public SwitchMemberDomainController(SwitchMemberDomainService domains, SwitchGatewayService gateway) {
        this.domains = domains;
        this.gateway = gateway;
    }

    @GetMapping("/{domain}")
    public SwitchDomainOverview overview(@PathVariable String domain,
            @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        if (!gateway.authorized(authorization)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid Switch member session");
        }
        try {
            return domains.overview(domain);
        } catch (IllegalArgumentException invalidDomain) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, invalidDomain.getMessage());
        }
    }
}
