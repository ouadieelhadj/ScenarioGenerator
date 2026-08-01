package com.staging.sg.threeds.network.api;

import com.staging.sg.common.threeds.*;
import com.staging.sg.threeds.network.service.ThreeDsNetworkSimulatorService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/3ds/network/v1")
public class ThreeDsNetworkSimulatorController {
    private final ThreeDsNetworkSimulatorService service;

    public ThreeDsNetworkSimulatorController(ThreeDsNetworkSimulatorService service) {
        this.service = service;
    }

    @PostMapping("/areq")
    public ThreeDsARes areq(@RequestBody ThreeDsAReq request) {
        return service.authenticate(request);
    }

    @PostMapping("/external-acs/creq")
    public ThreeDsCRes creq(@RequestBody ThreeDsCReq request) {
        return service.challenge(request);
    }

    @PostMapping("/rreq")
    public ThreeDsRRes rreq(@RequestBody ThreeDsRReq request) {
        return service.routeResult(request);
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of("status", "UP", "module", "sg-3ds-network-simulator",
                "programs", new String[] {"VISA", "MASTERCARD"},
                "messageVersion", ThreeDsNetworkSimulatorService.VERSION,
                "directoryServer", "SIMULATED");
    }
}
