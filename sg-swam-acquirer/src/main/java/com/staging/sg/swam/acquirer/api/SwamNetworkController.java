package com.staging.sg.swam.acquirer.api;

import com.staging.sg.swam.acquirer.network.SwamAuthorization;
import com.staging.sg.swam.acquirer.network.SwamJposClient;
import com.staging.sg.swam.acquirer.network.SwamMac;
import com.staging.sg.swam.acquirer.network.SwamPin;
import com.staging.sg.common.entity.SwamAcqTransaction;
import com.staging.sg.common.repository.SwamAcqTransactionRepository;
import com.staging.sg.common.iso.sid.SidMessageValidator;
import com.staging.sg.common.iso.sid.SidTransactionPersistenceMapper;
import org.jpos.iso.ISOMsg;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Pilotage SWAM cote Membre : gestion reseau (sign-on/echo/sign-off) + achat unitaire.
 *
 * SEQUENCEMENT DES CLES (corrige) :
 *   L'acquereur (membre) NE DEMANDE PAS les cles. Le SWITCH les POUSSE
 *   spontanement apres le sign-on (1804 DE24=811 pour la ZPK, 1804 DE24=899
 *   pour la ZAK). Ces push sont traites AUTOMATIQUEMENT par le receiver de
 *   SwamJposClient (import sous LMK + reponse 1814 DE39=800).
 *
 *   -> Les anciens endpoints /keyexchange/zpk et /keyexchange/zak, qui
 *      faisaient EMETTRE a l'acquereur des 1804/811 et 1804/899 (modele "pull"),
 *      ont ete RETIRES : le switch reel fonctionne en "push" et rejetait ces
 *      demandes avec DE39=880.
 */
@RestController
@RequestMapping("/api/admin/swam")
public class SwamNetworkController {

    private final SwamJposClient client;
    private final SwamAuthorization auth;
    private final SwamAcqTransactionRepository txRepo;
    private final SwamMac swamMac;
    private final SwamPin swamPin;

    public SwamNetworkController(SwamJposClient client, SwamAuthorization auth,
                                 SwamAcqTransactionRepository txRepo,
                                 SwamMac swamMac, SwamPin swamPin) {
        this.client = client;
        this.auth = auth;
        this.txRepo = txRepo;
        this.swamMac = swamMac;
        this.swamPin = swamPin;
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
        // Pose le DE128 (MAC) : X9.19 avec la ZMK (recette sortante validee).
        // La ZMK est la seule cle disponible avant l'echange ZPK/ZAK, donc elle
        // sert a MACer le sign-on (et les autres messages reseau).
        String macSent = swamMac.apply(req);
        ISOMsg resp = client.sendAndWait(req, 10);
        Map<String,Object> r = new LinkedHashMap<>();
        r.put("label", label);
        r.put("mti_sent", "1804");
        r.put("de24_func", func);
        r.put("stan", req.getString(11));
        r.put("de128_mac_sent", macSent);
        r.put("mti_received", resp.getMTI());
        r.put("de39_action", resp.hasField(39) ? resp.getString(39) : null);
        r.put("success", "800".equals(resp.hasField(39) ? resp.getString(39) : ""));
        return r;
    }

    @PostMapping("/purchase")
    public Map<String,Object> purchase(@RequestParam(defaultValue = "5321962145453348") String pan,
                                       @RequestParam(defaultValue = "000000010000") String amount,
                                       @RequestParam(required = false) String pin) throws Exception {
        client.connect();
        String stan = auth.nextStan_();
        ISOMsg req = auth.buildAuth1100(pan, amount, stan, client.getPackager());
        swamPin.apply(req, pin);               // pose DE52+DE53 si pin fourni
        String macSent = swamMac.apply(req);   // pose DE128 (MAC reel)
        SidMessageValidator.validate(req);
        ISOMsg resp = client.sendAndWait(req, 10);
        SidMessageValidator.validateResponseTo(req, resp);
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
            SidTransactionPersistenceMapper.populate(tx, req, resp);
            if ("1420".equals(req.getMTI()) && "000".equals(rc)) {
                txRepo.findFirstByRrnAndClearingEligibleTrueOrderByCreatedAtDesc(req.getString(37))
                        .ifPresent(original -> {
                            long current = original.getClearingAmount() != null
                                    ? original.getClearingAmount() : original.getAmount();
                            long remaining = Math.max(0L, current - Long.parseLong(amount));
                            boolean partial = "402".equals(req.getString(24));
                            original.setClearingAmount(remaining);
                            original.setClearingEligible(partial && remaining > 0L);
                            original.setLifecycleStatus(partial && remaining > 0L
                                    ? "PARTIALLY_REVERSED" : "REVERSED");
                            txRepo.save(original);
                        });
            }
            txRepo.save(tx);
        } catch (Exception e) {
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
        r.put("de128_mac_sent", macSent);
        r.put("pin_sent", pin != null);
        r.put("de128_mac_echo", resp.hasField(128) ? org.jpos.iso.ISOUtil.hexString(resp.getBytes(128)) : null);
        return r;
    }

    @PostMapping("/financial")
    public Map<String,Object> financial(
            @RequestParam(defaultValue = "5321962145453348") String pan,
            @RequestParam(defaultValue = "000000010000") String amount,
            @RequestParam(required = false) String pin) throws Exception {
        String stan = auth.nextStan_();
        ISOMsg request = auth.buildFinancial1200(pan, amount, stan, client.getPackager());
        return sendTransaction("FINANCIAL", request, pin, pan, amount, "1210");
    }

    @PostMapping("/financial-advice")
    public Map<String,Object> financialAdvice(
            @RequestParam(defaultValue = "5321962145453348") String pan,
            @RequestParam(defaultValue = "000000010000") String amount,
            @RequestParam String authorizationCode,
            @RequestParam String originalDataElements) throws Exception {
        String stan = auth.nextStan_();
        ISOMsg request = auth.buildFinancialAdvice1220(
                pan, amount, stan, authorizationCode, originalDataElements, client.getPackager());
        return sendTransaction("FINANCIAL_ADVICE", request, null, pan, amount, "1230");
    }

    @PostMapping("/reversal")
    public Map<String,Object> reversal(
            @RequestParam(defaultValue = "5321962145453348") String pan,
            @RequestParam(defaultValue = "000000010000") String amount,
            @RequestParam String rrn,
            @RequestParam String authorizationCode,
            @RequestParam String originalDataElements,
            @RequestParam(defaultValue = "false") boolean partial,
            @RequestParam(required = false) String originalAmounts) throws Exception {
        String stan = auth.nextStan_();
        ISOMsg request = auth.buildReversal1420(
                pan, amount, stan, rrn, authorizationCode, originalDataElements,
                partial, originalAmounts, client.getPackager());
        return sendTransaction("REVERSAL", request, null, pan, amount, "1430");
    }

    private Map<String,Object> sendTransaction(
            String type, ISOMsg req, String pin, String pan, String amount, String expectedResponseMti)
            throws Exception {
        client.connect();
        swamPin.apply(req, pin);
        String macSent = swamMac.apply(req);
        SidMessageValidator.validate(req);
        ISOMsg resp = client.sendAndWait(req, 10);
        if (!expectedResponseMti.equals(resp.getMTI())) {
            throw new IllegalStateException("Reponse SID attendue " + expectedResponseMti
                    + ", recue " + resp.getMTI());
        }
        SidMessageValidator.validateResponseTo(req, resp);
        persistTransaction(req, resp, pan, amount);

        Map<String,Object> result = new LinkedHashMap<>();
        result.put("type", type);
        result.put("mti_sent", req.getMTI());
        result.put("stan", req.getString(11));
        result.put("mti_received", resp.getMTI());
        result.put("de39_action", resp.hasField(39) ? resp.getString(39) : null);
        result.put("de38_auth", resp.hasField(38) ? resp.getString(38) : null);
        result.put("approved", "000".equals(resp.hasField(39) ? resp.getString(39) : ""));
        result.put("de128_mac_sent", macSent);
        return result;
    }

    private void persistTransaction(ISOMsg req, ISOMsg resp, String pan, String amount) {
        try {
            String rc = resp.hasField(39) ? resp.getString(39) : null;
            SwamAcqTransaction tx = new SwamAcqTransaction();
            tx.setPan(pan);
            tx.setStan(req.getString(11));
            tx.setTransmissionDt(req.getString(7));
            tx.setMti(req.getMTI());
            tx.setProcessingCode(req.getString(3));
            tx.setAmount(Long.parseLong(amount));
            tx.setCurrency(req.getString(49));
            tx.setResponseCode(rc);
            tx.setStatus("000".equals(rc) ? "APPROVED" : "DECLINED");
            SidTransactionPersistenceMapper.populate(tx, req, resp);
            txRepo.save(tx);
        } catch (Exception e) {
            throw new IllegalStateException("[SWAM-ACQ] Persistance transaction SID impossible", e);
        }
    }

    @GetMapping("/health")
    public Map<String,Object> health() {
        return Map.of("module","sg-swam-acquirer","role","Membre/banque",
                      "connected", client.isConnected(), "status","UP");
    }
}
