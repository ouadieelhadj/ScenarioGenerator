package com.staging.sg.mc.api;

import com.staging.sg.mc.network.McJposClient;
import org.jpos.iso.ISOMsg;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Controleur REST Mastercard SMS — gestion reseau.
 *
 * Endpoints :
 *   POST /api/admin/mc/network/signon   -> 0800 DE70=061
 *   POST /api/admin/mc/network/echo     -> 0800 DE70=270
 *   POST /api/admin/mc/network/signoff  -> 0800 DE70=062
 *   GET  /api/admin/mc/health
 */
@RestController
@RequestMapping("/api/admin/mc")
public class McNetworkController {

    private final McJposClient client;

    public McNetworkController(McJposClient client) {
        this.client = client;
    }

    @PostMapping("/network/signon")
    public Map<String,Object> signon() throws Exception {
        ISOMsg resp = client.signon();
        return buildResponse("SIGN-ON", "0800", "061", resp);
    }

    @PostMapping("/network/echo")
    public Map<String,Object> echo() throws Exception {
        ISOMsg resp = client.echo();
        return buildResponse("ECHO", "0800", "270", resp);
    }

    @PostMapping("/network/signoff")
    public Map<String,Object> signoff() throws Exception {
        ISOMsg resp = client.signoff();
        return buildResponse("SIGN-OFF", "0800", "062", resp);
    }

    @GetMapping("/health")
    public Map<String,Object> health() {
        return Map.of(
                "module", "sg-mc",
                "role",   "Mastercard SMS",
                "connected", client.isConnected(),
                "status", "UP"
        );
    }

    private Map<String,Object> buildResponse(String label, String mtiSent,
                                              String de70, ISOMsg resp) throws Exception {
        Map<String,Object> r = new LinkedHashMap<>();
        r.put("label",        label);
        r.put("mti_sent",     mtiSent);
        r.put("de70",         de70);
        r.put("mti_received", resp != null ? resp.getMTI() : null);
        r.put("de39",         resp != null && resp.hasField(39) ? resp.getString(39) : null);
        r.put("success",      resp != null && "00".equals(
                resp.hasField(39) ? resp.getString(39) : ""));
        return r;
    }
}
