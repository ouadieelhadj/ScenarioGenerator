package com.staging.sg.mc.dmas.member.api;

import com.staging.sg.mc.dmas.member.network.McDmasMemberClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Gestion reseau cote MEMBRE : sign-on, sign-off, echo.
 *
 * Tout passe par la LIAISON PERMANENTE de McDmasMemberClient. Le
 * mecanisme a connexion ephemere (McDmasNetworkManager +
 * McDmasNetworkUtil.sendAndReceive) a ete supprime : il ouvrait et
 * fermait une socket a chaque message, ce qui est incompatible avec
 * une liaison permanente et empechait de recevoir les messages
 * pousses par le reseau.
 */
@RestController
@RequestMapping("/api/admin/dmas/network")
public class McDmasNetworkController {

    private final McDmasMemberClient client;

    public McDmasNetworkController(McDmasMemberClient client) {
        this.client = client;
    }

    @PostMapping("/signon")
    public ResponseEntity<?> signon() {
        return run(client::signOn);
    }

    @PostMapping("/signoff")
    public ResponseEntity<?> signoff() {
        return run(client::signOff);
    }

    @PostMapping("/echo")
    public ResponseEntity<?> echo() {
        return run(client::echoTest);
    }

    @GetMapping("/status")
    public ResponseEntity<?> status() {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("role",            "CLIENT");
        r.put("connected",       client.isConnected());
        r.put("signed_on",       client.hasActiveSession());
        r.put("member_group_id", client.getActiveMemberGroupId());
        return ResponseEntity.ok(r);
    }

    // ------------------------------------------------------------

    @FunctionalInterface
    private interface NetworkCall {
        Map<String, Object> call() throws Exception;
    }

    private ResponseEntity<?> run(NetworkCall c) {
        try {
            return ResponseEntity.ok(c.call());
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", String.valueOf(e.getMessage())));
        }
    }
}
