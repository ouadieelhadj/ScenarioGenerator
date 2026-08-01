package com.staging.sg.waypos.server.api;

import com.staging.sg.waypos.server.service.WayPosLocalRkiBootstrapService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@Profile("connected-e2e")
@ConditionalOnProperty(
        name = "way-pos.local-test-bootstrap-enabled", havingValue = "true")
@RequestMapping("/api/admin/waypos/v1/local-test")
public class WayPosLocalRkiBootstrapController {
    private final WayPosLocalRkiBootstrapService service;

    public WayPosLocalRkiBootstrapController(
            WayPosLocalRkiBootstrapService service) {
        this.service = service;
    }

    @PostMapping("/terminals/{terminalId}/rki-bootstrap")
    public ResponseEntity<?> bootstrap(@PathVariable String terminalId) {
        try {
            return ResponseEntity.ok(service.bootstrap(terminalId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", e.getClass().getSimpleName()
                            + ": " + e.getMessage()));
        }
    }
}
