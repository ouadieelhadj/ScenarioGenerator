package com.staging.sg.acquirer.api;

import com.staging.sg.acquirer.service.CampaignCrudService;
import com.staging.sg.acquirer.service.CampaignRunService;
import com.staging.sg.common.dto.CampaignDto;
import com.staging.sg.common.dto.CampaignRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/campaigns")
public class CampaignController {

    private static final Logger log = LoggerFactory.getLogger(CampaignController.class);

    private final CampaignRunService runService;
    private final CampaignCrudService crudService;

    public CampaignController(CampaignRunService runService,
                             CampaignCrudService crudService) {
        this.runService = runService;
        this.crudService = crudService;
    }

    // ===== Lancement (existant) =====
    /** Lance une campagne (multi-paliers, async). Retourne campaignExecutionId. */
    @PreAuthorize("hasAuthority('CAMPAIGN_GENERATE')")
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

    // ===== CRUD =====

    @PreAuthorize("hasAuthority('CAMPAIGN_VIEW')")
    @GetMapping
    public ResponseEntity<List<CampaignDto>> findAll() {
        return ResponseEntity.ok(crudService.findAll());
    }

    @PreAuthorize("hasAuthority('CAMPAIGN_VIEW')")
    @GetMapping("/{id}")
    public ResponseEntity<?> findById(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(crudService.findById(id));
        } catch (Exception e) {
            return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
        }
    }

    @PreAuthorize("hasAuthority('CAMPAIGN_CREATE')")
    @PostMapping
    public ResponseEntity<?> create(@RequestBody CampaignRequest req, Authentication auth) {
        try {
            return ResponseEntity.ok(crudService.create(req, auth.getName()));
        } catch (Exception e) {
            log.error("[API] campaign create error : {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PreAuthorize("hasAuthority('CAMPAIGN_CREATE')")
    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody CampaignRequest req) {
        try {
            return ResponseEntity.ok(crudService.update(id, req));
        } catch (Exception e) {
            log.error("[API] campaign update error : {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PreAuthorize("hasAuthority('CAMPAIGN_CREATE')")
    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        try {
            crudService.delete(id);
            return ResponseEntity.ok(Map.of("message", "Campagne supprimee", "id", id));
        } catch (Exception e) {
            log.error("[API] campaign delete error : {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
