package com.staging.sg.visa.visanet.simulator.api;

import com.staging.sg.visa.common.online.VisaOnlineNetworkEnvelope;
import com.staging.sg.visa.visanet.simulator.service.VisaNetSimulatorService;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/visa/network/v1")
public class VisaNetSimulatorController {
    private final VisaNetSimulatorService service;
    public VisaNetSimulatorController(VisaNetSimulatorService service) { this.service = service; }

    @PostMapping("/messages")
    public VisaOnlineNetworkEnvelope exchange(@RequestBody VisaOnlineNetworkEnvelope envelope) {
        return service.exchange(envelope);
    }

    @GetMapping("/health") public Map<String, String> health() {
        return Map.of("status", "UP", "module", "SG_VISA_VISANET_SIMULATOR",
                "provenance", "SIMULATED_NETWORK");
    }

    @GetMapping("/capabilities") public Map<String, Object> capabilities() {
        return Map.of("messages", java.util.List.of("0100/0110", "0400/0410", "0420/0430", "0800/0810"),
                "certified", false, "provenance", "SIMULATED_NETWORK");
    }
}
