package com.staging.sg.visa.online.member.api;

import com.staging.sg.common.routing.*;
import com.staging.sg.visa.online.member.service.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
public class VisaOnlineMemberController {
    private final VisaOnlineMemberService service;
    public VisaOnlineMemberController(VisaOnlineMemberService service) { this.service = service; }

    @PostMapping("/api/routing/v1/transactions")
    public ResponseEntity<?> route(@RequestBody RoutingTransactionRequest request) {
        try { return ResponseEntity.ok(service.authorize(request)); }
        catch (IllegalArgumentException e) { return ResponseEntity.badRequest().body(Map.of("error", e.getMessage())); }
        catch (IllegalStateException e) { return ResponseEntity.status(503).body(Map.of("error", e.getMessage(), "retryable", false)); }
    }

    @GetMapping("/api/visa/online/v1/transactions")
    public List<VisaOnlineJournalView> transactions() { return service.journal(); }

    @GetMapping("/api/visa/online/v1/transactions/{id}")
    public ResponseEntity<VisaOnlineJournalView> transaction(@PathVariable String id) {
        return service.find(id).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/api/visa/online/v1/health") public Map<String, String> health() {
        return Map.of("status", "UP", "module", "SG_VISA_ONLINE_MEMBER");
    }
}
