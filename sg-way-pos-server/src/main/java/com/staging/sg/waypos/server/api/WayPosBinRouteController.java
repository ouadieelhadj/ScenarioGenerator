package com.staging.sg.waypos.server.api;

import com.staging.sg.waypos.server.domain.PosBinRoute;
import com.staging.sg.waypos.server.repository.PosBinRouteRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/waypos/v1/bin-routes")
public class WayPosBinRouteController {
    private final PosBinRouteRepository routes;

    public WayPosBinRouteController(PosBinRouteRepository routes) {
        this.routes = routes;
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody BinRouteRequest request) {
        try {
            PosBinRoute route = routes.save(PosBinRoute.active(
                    request.binFrom(), request.binTo(),
                    request.interfaceCode(), request.priority()));
            return ResponseEntity.status(HttpStatus.CREATED).body(view(route));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deactivate(@PathVariable long id) {
        return routes.findById(id).<ResponseEntity<?>>map(route -> {
            route.deactivate();
            routes.save(route);
            return ResponseEntity.ok(view(route));
        }).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping
    public List<Map<String, Object>> list() {
        return routes.findAll().stream().map(WayPosBinRouteController::view).toList();
    }

    private static Map<String, Object> view(PosBinRoute value) {
        return Map.of(
                "id", value.getId(),
                "binFrom", value.getBinFrom(),
                "binTo", value.getBinTo(),
                "interfaceCode", value.getInterfaceCode(),
                "priority", value.getPriority(),
                "active", value.isActive());
    }

    public record BinRouteRequest(
            String binFrom, String binTo, String interfaceCode, int priority) {}
}
