package com.staging.sg.swam.acquirer.api;

import com.staging.sg.swam.acquirer.network.SwamAuthorization;
import com.staging.sg.swam.acquirer.network.SwamJposClient;
import org.jpos.iso.ISOMsg;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Pilotage SWAM cote Membre : gestion reseau (sign-on/echo/sign-off) + achat unitaire.
 * Le sign-on etablit la connexion permanente (conforme a la decision : sign-on par REST).
 */
@RestController
@RequestMapping("/api/admin/swam")
public class SwamNetworkController {

    private final SwamJposClient client;
    private final SwamAuthorization auth;

    public SwamNetworkController(SwamJposClient client, SwamAuthorization auth) {
        this.client = client;
        this.auth = auth;
    }

    @PostMapping("/network/signon")
    public Map<String,Object> signon() throws Exception {
        return network("801", "SIGN-ON");
    }

    @PostMapping("/network/echo")
    public Map<String,Object> echo() throws Exception {
        return network("803", "ECHO-TEST");
    }

    @PostMapping("/network/signoff")
    public Map<String,Object> signoff() throws Exception {
        return network("802", "SIGN-OFF");
    }

    private Map<String,Object> network(String func, String label) throws Exception {
        client.connect();
        ISOMsg req = auth.buildNetwork(func, client.getPackager());
        ISOMsg resp = client.sendAndWait(req, 10);
        Map<String,Object> r = new LinkedHashMap<>();
        r.put("label", label);
        r.put("mti_sent", "1804");
        r.put("de24_func", func);
        r.put("stan", req.getString(11));
        r.put("mti_received", resp.getMTI());
        r.put("de39_action", resp.hasField(39) ? resp.getString(39) : null);
        r.put("success", "800".equals(resp.hasField(39) ? resp.getString(39) : ""));
        return r;
    }

    @PostMapping("/purchase")
    public Map<String,Object> purchase(@RequestParam(defaultValue = "5321962145453348") String pan,
                                       @RequestParam(defaultValue = "000000010000") String amount) throws Exception {
        client.connect();
        String stan = auth.nextStan_();
        ISOMsg req = auth.buildAuth1100(pan, amount, stan, client.getPackager());
        ISOMsg resp = client.sendAndWait(req, 10);
        Map<String,Object> r = new LinkedHashMap<>();
        r.put("type", "PURCHASE");
        r.put("mti_sent", "1100");
        r.put("stan", stan);
        r.put("mti_received", resp.getMTI());
        r.put("de39_action", resp.hasField(39) ? resp.getString(39) : null);
        r.put("de38_auth", resp.hasField(38) ? resp.getString(38) : null);
        r.put("approved", "000".equals(resp.hasField(39) ? resp.getString(39) : ""));
        return r;
    }

    @GetMapping("/health")
    public Map<String,Object> health() {
        return Map.of("module","sg-swam-acquirer","role","Membre/banque",
                      "connected", client.isConnected(), "status","UP");
    }
}
