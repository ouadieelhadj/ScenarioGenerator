package com.staging.sg.mc.dmas.member.api;

import com.staging.sg.common.service.McDmasInterfaceService;
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
    private final McDmasInterfaceService iface;

    public McDmasNetworkController(McDmasMemberClient client,
                                   McDmasInterfaceService iface) {
        this.client = client;
        this.iface = iface;
    }

    @PostMapping("/signon")
    public ResponseEntity<?> signon(@RequestParam(required = false) String bank) {
        return run(() -> client.signOn(bank));
    }

    @PostMapping("/signoff")
    public ResponseEntity<?> signoff(@RequestParam(required = false) String bank) {
        return run(() -> client.signOff(bank));
    }

    @PostMapping("/echo")
    public ResponseEntity<?> echo(@RequestParam(required = false) String bank) {
        return run(() -> client.echoTest(bank));
    }

    /** Sign-on de TOUTES les banques pilotees. */
    @PostMapping("/signon-all")
    public ResponseEntity<?> signonAll() {
        Map<String, Object> r = new LinkedHashMap<>();
        for (String b : iface.bankCodes()) {
            try {
                r.put(b, client.signOn(b));
            } catch (Exception e) {
                r.put(b, Map.of("error", String.valueOf(e.getMessage())));
            }
        }
        return ResponseEntity.ok(r);
    }

    /** Etat d'une banque, ou de toutes si ?bank= est omis en multi. */
    @GetMapping("/status")
    public ResponseEntity<?> status(@RequestParam(required = false) String bank) {
        if (bank == null && iface.isMulti()) {
            Map<String, Object> all = new LinkedHashMap<>();
            for (String b : iface.bankCodes()) all.put(b, statusOf(b));
            return ResponseEntity.ok(all);
        }
        return ResponseEntity.ok(statusOf(bank));
    }

    private Map<String, Object> statusOf(String bank) {
        var cfg = iface.byBank(bank);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("interface",       cfg.getIdInterface());
        r.put("bank_code",       cfg.getBankCode());
        r.put("label",           cfg.getLabel());
        r.put("status",          iface.status(cfg.getBankCode()));
        r.put("role",            "CLIENT");
        r.put("connected",       client.isConnected(cfg.getBankCode()));
        r.put("signed_on",       client.hasActiveSession(cfg.getBankCode()));
        r.put("group_signon_de2",cfg.getGroupSignonDe2());
        r.put("member_group_id", cfg.getMemberGroupId());
        r.put("target",          cfg.getTargetHost() + ":" + cfg.getTargetPort());
        return r;
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
