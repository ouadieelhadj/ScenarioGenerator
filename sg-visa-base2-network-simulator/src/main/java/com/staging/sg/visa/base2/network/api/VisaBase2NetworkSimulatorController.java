package com.staging.sg.visa.base2.network.api;

import com.staging.sg.visa.base2.common.*;
import com.staging.sg.visa.base2.network.service.VisaBase2NetworkSimulatorService;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/visa/base2/network/v1")
public class VisaBase2NetworkSimulatorController {
    private final VisaBase2NetworkSimulatorService service;
    public VisaBase2NetworkSimulatorController(VisaBase2NetworkSimulatorService service) { this.service = service; }
    @PostMapping("/files") public VisaBase2NetworkAck receive(@RequestBody VisaBase2NetworkFileEnvelope file) { return service.receive(file); }
    @GetMapping("/files") public List<VisaBase2NetworkAck> files() { return service.files(); }
    @GetMapping("/health") public Map<String, String> health() {
        return Map.of("status", "UP", "module", "SG_VISA_BASE2_NETWORK_SIMULATOR",
                "provenance", "SIMULATED_NETWORK");
    }
    @GetMapping("/capabilities") public Map<String, Object> capabilities() {
        return Map.of("format", "CTF", "recordLength", 168,
                "transactions", List.of("TC05/TCR0", "TC05/TCR5", "TC90", "TC91", "TC92"),
                "itfAvailable", false, "certified", false);
    }
}
