package com.staging.sg.switchlab.bff.api;

import com.staging.sg.switchlab.bff.service.SwitchLabGatewayService;
import com.staging.sg.switchlab.bff.service.SwitchLabOverviewService;
import com.staging.sg.switchlab.bff.service.SwitchLabTraceService;
import com.staging.sg.switchlab.contracts.SwitchLabEnvironmentReference;
import com.staging.sg.switchlab.contracts.SwitchLabOverview;
import com.staging.sg.switchlab.contracts.SwitchLabProductStatus;
import com.staging.sg.switchlab.contracts.SwitchLabTraceEvent;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/switchlab/v1")
public class SwitchLabProductController {
    private final SwitchLabOverviewService overviewService;
    private final SwitchLabGatewayService gateway;
    private final SwitchLabTraceService traces;

    public SwitchLabProductController(SwitchLabOverviewService overviewService, SwitchLabGatewayService gateway,
                                      SwitchLabTraceService traces) {
        this.overviewService = overviewService;
        this.gateway = gateway;
        this.traces = traces;
    }

    @GetMapping({"/health", "/product"})
    public SwitchLabProductStatus status() {
        return new SwitchLabProductStatus("1.0", "SWITCHLAB",
                "FuturPayment SwitchLab", "SIMULATORS_ONLY", "UP", Instant.now());
    }

    @GetMapping("/environments")
    public List<SwitchLabEnvironmentReference> environments(
            @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        requireAuthorized(authorization);
        return overviewService.environments();
    }

    @GetMapping("/overview")
    public SwitchLabOverview overview(
            @RequestParam String environmentId,
            @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        requireAuthorized(authorization);
        try {
            return overviewService.overview(environmentId);
        } catch (IllegalArgumentException invalidEnvironment) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, invalidEnvironment.getMessage());
        }
    }

    @GetMapping("/traces")
    public List<SwitchLabTraceEvent> traces(
            @RequestParam String environmentId,
            @RequestParam(defaultValue = "100") int limit,
            @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        requireAuthorized(authorization);
        overviewService.environments().stream().filter(item -> item.id().equals(environmentId)).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Unknown or inactive SwitchLab environment"));
        return traces.latest(limit);
    }

    private void requireAuthorized(String authorization) {
        if (!gateway.authorized(authorization)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid SwitchLab session");
        }
    }
}
