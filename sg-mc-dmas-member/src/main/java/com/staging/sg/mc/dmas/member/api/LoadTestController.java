package com.staging.sg.mc.dmas.member.api;

import com.staging.sg.mc.dmas.member.network.LoadTestService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/admin/dmas/loadtest")
public class LoadTestController {

    private final LoadTestService service;

    public LoadTestController(LoadTestService service) {
        this.service = service;
    }

    /** Lance un load test asynchrone. Retourne le loadTestId. */
    @PostMapping
    public ResponseEntity<?> start(@RequestBody LoadTestRequest req) {
        if (req.pan == null || req.pan.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "pan requis"));
        }
        String id = service.start(req);
        if (id == null) {
            return ResponseEntity.status(429).body(Map.of(
                "error", "Limite de tests de charge simultanes atteinte (voir dmas.loadtest.max-concurrent-tests)"));
        }
        return ResponseEntity.ok(Map.of("loadTestId", id, "status", "RUNNING"));
    }

    /** Statut + metriques (et detail si COMPLETED). */
    @GetMapping("/{id}/status")
    public ResponseEntity<?> status(@PathVariable String id, 
                                    @RequestParam(defaultValue = "false") boolean details) {
        LoadTestService.LoadTestRun run = service.status(id);
        if (run == null) return ResponseEntity.notFound().build();

        Map<String,Object> r = new LinkedHashMap<>();
        r.put("loadTestId", id);
        r.put("status", run.status);
        r.put("sent", run.sent.get());
        r.put("approved", run.approved.get());
        r.put("declined", run.declined.get());
        r.put("errors", run.errors.get());
        r.put("de39Counts", run.de39Counts);
        long dur = (run.endedAt > 0 ? run.endedAt : System.currentTimeMillis()) - run.startedAt;
        r.put("durationMs", dur);
        r.put("actualTps", dur > 0 ? run.sent.get() * 1000.0 / dur : 0);
        if (details && "COMPLETED".equals(run.status)) {
            r.put("details", run.details);
        }
        return ResponseEntity.ok(r);
    }
}
