package com.staging.sg.way4aura.api;

import com.staging.sg.way4aura.service.Way4DryRunService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/internal/way4-aura/v1")
public class Way4DryRunController {
    private final Way4DryRunService service;
    public Way4DryRunController(Way4DryRunService service) { this.service = service; }
    @PostMapping("/dry-runs")
    @PreAuthorize("hasAuthority('SCOPE_way4.generate')")
    public Way4DryRunService.DryRunResult dryRun(@RequestBody Way4DryRunRequest request) {
        return service.generate(request);
    }
}
