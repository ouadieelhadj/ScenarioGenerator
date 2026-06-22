package com.staging.sg.acquirer.orchestration;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Endpoints d'orchestration : provisionnement des cartes, puis (à venir) rejeu contre DMAS.
 */
@RestController
@RequestMapping("/api/campaigns")
public class OrchestrationController {

    private final CardProvisioningService provisioning;

    public OrchestrationController(CardProvisioningService provisioning) {
        this.provisioning = provisioning;
    }

    /** Provisionne les cartes côté issuer DMAS pour les PAN de la campagne. */
    @PostMapping("/{id}/provision-cards")
    public ResponseEntity<?> provisionCards(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(provisioning.provision(id));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", String.valueOf(e.getMessage())));
        }
    }
}
