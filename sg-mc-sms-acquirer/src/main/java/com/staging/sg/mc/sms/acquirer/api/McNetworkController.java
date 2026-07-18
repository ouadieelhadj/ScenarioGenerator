package com.staging.sg.mc.sms.acquirer.api;

import com.staging.sg.mc.sms.acquirer.network.McSmsJposClient;
import org.jpos.iso.ISOMsg;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Controleur REST Mastercard SMS — gestion reseau (cote membre/acquereur).
 *
 * Endpoints :
 *   POST /api/admin/mc/network/signon   -> 0800 DE70=061
 *   POST /api/admin/mc/network/echo     -> 0800 DE70=270
 *   POST /api/admin/mc/network/signoff  -> 0800 DE70=062
 *   POST /api/admin/mc/network/connect  -> etablit la liaison permanente
 *   GET  /api/admin/mc/health
 */
@RestController
@RequestMapping("/api/admin/mc")
public class McNetworkController {

    private final McSmsJposClient client;

    public McNetworkController(McSmsJposClient client) {
        this.client = client;
    }

    @PostMapping("/network/connect")
    public Map<String,Object> connect() throws Exception {
        client.connect();
        return Map.of("connected", client.isConnected());
    }

    @PostMapping("/network/signon")
    public Map<String,Object> signon() throws Exception {
        return buildResponse("SIGN-ON", "061", client.signon());
    }

    @PostMapping("/network/echo")
    public Map<String,Object> echo() throws Exception {
        return buildResponse("ECHO TEST", "270", client.echo());
    }

    @PostMapping("/network/signoff")
    public Map<String,Object> signoff() throws Exception {
        return buildResponse("SIGN-OFF", "062", client.signoff());
    }

    @GetMapping("/health")
    public Map<String,Object> health() {
        return Map.of(
                "module",    "sg-mc-sms-acquirer",
                "role",      "Mastercard SMS — membre",
                "connected", client.isConnected(),
                "status",    "UP"
        );
    }

    private Map<String,Object> buildResponse(String label, String de70, ISOMsg resp) throws Exception {
        Map<String,Object> r = new LinkedHashMap<>();
        r.put("label",        label);
        r.put("mti_sent",     "0800");
        r.put("de70",         de70);
        r.put("mti_received", resp != null ? resp.getMTI() : null);
        r.put("de39",         resp != null && resp.hasField(39) ? resp.getString(39) : null);
        r.put("stan",         resp != null && resp.hasField(11) ? resp.getString(11) : null);
        r.put("success",      resp != null && resp.hasField(39) && "00".equals(resp.getString(39)));
        return r;
    }
}
