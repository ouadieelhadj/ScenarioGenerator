package com.staging.sg.acquirer.api;

import com.staging.sg.acquirer.service.CampaignRunService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/campaigns")
public class CampaignController {

    private static final Logger log = LoggerFactory.getLogger(CampaignController.class);
    private final CampaignRunService runService;

    public CampaignController(CampaignRunService runService) {
        this.runService = runService;
    }

    /** Lance une campagne (multi-paliers, async). Retourne campaignExecutionId. */
    @PostMapping("/{id}/run")
    public ResponseEntity<?> run(@PathVariable Long id, Authentication auth) {
        try {
            Map<String,Object> result = runService.run(id, auth.getName());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("[API] campaign run error : {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }
}
