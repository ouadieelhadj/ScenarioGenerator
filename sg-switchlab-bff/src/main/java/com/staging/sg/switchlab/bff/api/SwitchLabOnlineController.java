package com.staging.sg.switchlab.bff.api;

import com.staging.sg.switchlab.bff.config.SwitchLabCorrelationFilter;
import com.staging.sg.switchlab.bff.service.SwitchLabGatewayService;
import com.staging.sg.switchlab.bff.service.SwitchLabOnlineService;
import com.staging.sg.switchlab.contracts.SwitchLabOnlineNetwork;
import com.staging.sg.switchlab.contracts.SwitchLabOnlineKeyStatus;
import com.staging.sg.switchlab.contracts.SwitchLabOnlineScenario;
import com.staging.sg.switchlab.contracts.SwitchLabOnlineScenarioResult;
import com.staging.sg.switchlab.contracts.SwitchLabOnlineSession;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/switchlab/v1/online")
public class SwitchLabOnlineController {
    private final SwitchLabGatewayService authentication;
    private final SwitchLabOnlineService service;
    public SwitchLabOnlineController(SwitchLabGatewayService authentication, SwitchLabOnlineService service) {
        this.authentication = authentication; this.service = service;
    }
    @GetMapping("/networks")
    public List<SwitchLabOnlineNetwork> networks(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        requireAuthorized(authorization); return service.networks();
    }
    @GetMapping("/networks/{code}/session")
    public SwitchLabOnlineSession session(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
                                          @PathVariable String code) {
        requireAuthorized(authorization); return service.session(code);
    }
    @GetMapping("/networks/{code}/keys")
    public SwitchLabOnlineKeyStatus keys(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
                                         @PathVariable String code) {
        requireAuthorized(authorization); return service.keyStatus(code);
    }
    @GetMapping("/scenarios")
    public List<SwitchLabOnlineScenario> scenarios(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        requireAuthorized(authorization); return service.scenarios();
    }
    @org.springframework.web.bind.annotation.PostMapping("/scenarios/{code}/run")
    public SwitchLabOnlineScenarioResult run(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
                                             @PathVariable String code, HttpServletRequest request) {
        requireAuthorized(authorization);
        return service.execute(code, String.valueOf(request.getAttribute(SwitchLabCorrelationFilter.HEADER)));
    }
    private void requireAuthorized(String authorization) {
        if (!authentication.authorized(authorization))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid SwitchLab session");
    }
}
