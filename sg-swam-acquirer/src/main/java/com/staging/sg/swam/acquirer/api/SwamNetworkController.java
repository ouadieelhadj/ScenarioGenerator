package com.staging.sg.swam.acquirer.api;

import com.staging.sg.swam.acquirer.network.SwamAuthorization;
import com.staging.sg.swam.acquirer.network.SwamJposClient;
import com.staging.sg.swam.acquirer.network.SwamKeyExchange;
import com.staging.sg.common.entity.SwamAcqTransaction;
import com.staging.sg.common.repository.SwamAcqTransactionRepository;
import java.time.LocalDateTime;
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
    private final SwamAcqTransactionRepository txRepo;
    private final SwamKeyExchange keyExchange;

    public SwamNetworkController(SwamJposClient client, SwamAuthorization auth,
                                SwamAcqTransactionRepository txRepo, SwamKeyExchange keyExchange) {
        this.client = client;
        this.auth = auth;
        this.txRepo = txRepo;
        this.keyExchange = keyExchange;
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

    @PostMapping("/keyexchange/zpk")
    public Map<String,Object> keyExchangeZpk() throws Exception {
        return keyExchange.exchangeZpk();
    }

    @PostMapping("/keyexchange/zak")
    public Map<String,Object> keyExchangeZak() throws Exception {
        return keyExchange.exchangeZak();
    }

    @PostMapping("/purchase")
    public Map<String,Object> purchase(@RequestParam(defaultValue = "5321962145453348") String pan,
                                       @RequestParam(defaultValue = "000000010000") String amount) throws Exception {
        client.connect();
        String stan = auth.nextStan_();
        ISOMsg req = auth.buildAuth1100(pan, amount, stan, client.getPackager());
        ISOMsg resp = client.sendAndWait(req, 10);
        String rc = resp.hasField(39) ? resp.getString(39) : null;

        // Persister la transaction emise cote acquereur
        try {
            SwamAcqTransaction tx = new SwamAcqTransaction();
            tx.setPan(pan);
            tx.setStan(stan);
            tx.setTransmissionDt(req.hasField(7) ? req.getString(7) : "");
            tx.setMti("1100");
            tx.setProcessingCode(req.hasField(3) ? req.getString(3) : null);
            tx.setAmount(Long.parseLong(amount));
            tx.setCurrency(req.hasField(49) ? req.getString(49) : null);
            tx.setResponseCode(rc);
            tx.setStatus("000".equals(rc) ? "APPROVED" : "DECLINED");
            txRepo.save(tx);
        } catch (Exception e) {
            // log seulement, ne bloque pas la reponse
            System.err.println("[SWAM-ACQ] Persistance tx KO : " + e.getMessage());
        }

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
