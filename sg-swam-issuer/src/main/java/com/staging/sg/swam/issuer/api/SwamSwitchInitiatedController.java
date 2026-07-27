package com.staging.sg.swam.issuer.api;

import com.staging.sg.swam.issuer.network.SwamJposServer;
import org.jpos.iso.ISOMsg;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Transactions initiées par le switch sur la liaison SID permanente déjà
 * ouverte par le membre. Aucune seconde connexion réseau n'est créée.
 */
@RestController
@RequestMapping("/api/admin/swam")
public class SwamSwitchInitiatedController {

    private final SwamJposServer server;

    public SwamSwitchInitiatedController(SwamJposServer server) {
        this.server = server;
    }

    @PostMapping("/purchase")
    public Map<String, Object> purchase(
            @RequestParam(defaultValue = "5321962145453348") String pan,
            @RequestParam(defaultValue = "000000010000") String amount) throws Exception {
        ISOMsg response = server.initiatePurchase(pan, amount);
        String responseCode = response.hasField(39) ? response.getString(39) : null;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", "PURCHASE");
        result.put("direction", "SWITCH_TO_MEMBER");
        result.put("connection", "PERMANENT_SID");
        result.put("mti_received", response.getMTI());
        result.put("stan", response.hasField(11) ? response.getString(11) : null);
        result.put("de39_action", responseCode);
        result.put("approved", "000".equals(responseCode));
        return result;
    }

    @PostMapping("/financial")
    public Map<String, Object> financial(
            @RequestParam(defaultValue = "5321962145453348") String pan,
            @RequestParam(defaultValue = "000000010000") String amount) throws Exception {
        ISOMsg response = server.initiateFinancial(pan, amount);
        String responseCode = response.hasField(39) ? response.getString(39) : null;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", "FINANCIAL_PURCHASE");
        result.put("direction", "SWITCH_TO_MEMBER");
        result.put("connection", "PERMANENT_SID");
        result.put("mti_received", response.getMTI());
        result.put("stan", response.hasField(11) ? response.getString(11) : null);
        result.put("de39_action", responseCode);
        result.put("approved", "000".equals(responseCode));
        return result;
    }

    @GetMapping("/connection")
    public Map<String, Object> connection() {
        return Map.of(
                "connected", server.hasPermanentConnection(),
                "mode", "SINGLE_PERMANENT_BIDIRECTIONAL");
    }
}
