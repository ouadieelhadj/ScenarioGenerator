package com.staging.sg.visa.base2.member.api;

import com.staging.sg.visa.base2.member.service.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/visa/base2/v1")
public class VisaBase2MemberController {
    private final VisaBase2MemberService service;
    public VisaBase2MemberController(VisaBase2MemberService service) { this.service = service; }
    @PostMapping("/presentments") public ResponseEntity<?> present(@RequestBody VisaBase2PresentmentRequest request) {
        try { return ResponseEntity.ok(service.present(request)); }
        catch (IllegalArgumentException e) { return ResponseEntity.badRequest().body(Map.of("error", e.getMessage())); }
        catch (IllegalStateException e) { return ResponseEntity.status(503).body(Map.of("error", e.getMessage(), "retryable", false)); }
    }
    @GetMapping("/files") public List<VisaBase2MemberFileView> files() { return service.files(); }
    @GetMapping("/health") public Map<String, String> health() { return Map.of("status", "UP", "module", "SG_VISA_BASE2_MEMBER"); }
}
