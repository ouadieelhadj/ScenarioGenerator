package com.staging.sg.swam.issuer.api;

import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/swam/issuer")
public class HealthController {
    @GetMapping("/health")
    public Map<String,Object> health() {
        return Map.of("module","sg-swam-issuer","role","switch/CENTRE","status","UP");
    }
}
