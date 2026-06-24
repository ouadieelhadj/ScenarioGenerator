package com.staging.sg.dmas.issuer.api;

import com.staging.sg.dmas.issuer.issuer.DmasJposClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** Endpoints pour declencher le sign-on / echo via le canal jPOS (etape A). */
@RestController
@RequestMapping("/api/admin/dmas/jpos")
public class JposNetworkController {

    private final DmasJposClient client;

    public JposNetworkController(DmasJposClient client) {
        this.client = client;
    }

    @PostMapping("/signon")
    public ResponseEntity<?> signon() {
        try { return ResponseEntity.ok(client.signOn()); }
        catch (Exception e) { return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage())); }
    }

    @PostMapping("/echo")
    public ResponseEntity<?> echo() {
        try { return ResponseEntity.ok(client.echoTest()); }
        catch (Exception e) { return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage())); }
    }
}
