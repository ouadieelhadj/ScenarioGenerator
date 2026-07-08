package com.staging.sg.acquirer.api;

import com.staging.sg.common.entity.NetworkRef;
import com.staging.sg.common.repository.NetworkRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Referentiel des reseaux (lecture), pour alimenter les selecteurs du front.
 * GET /api/networks           -> tous les reseaux actifs
 * GET /api/networks/{code}    -> un reseau par code
 */
@RestController
@RequestMapping("/api/networks")
public class NetworkController {

    private final NetworkRepository networkRepository;

    public NetworkController(NetworkRepository networkRepository) {
        this.networkRepository = networkRepository;
    }

    @GetMapping
    public ResponseEntity<List<NetworkRef>> findAllActive() {
        return ResponseEntity.ok(networkRepository.findByActiveTrue());
    }

    @GetMapping("/{code}")
    public ResponseEntity<NetworkRef> findByCode(@PathVariable String code) {
        return networkRepository.findByCode(code)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
