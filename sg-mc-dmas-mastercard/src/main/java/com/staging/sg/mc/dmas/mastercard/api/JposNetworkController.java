package com.staging.sg.mc.dmas.mastercard.api;

import com.staging.sg.common.iso.McPackagerEbcdic;
import com.staging.sg.mc.dmas.mastercard.network.McDmasMastercardServer;
import org.jpos.iso.ISOMsg;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Pilotage du reseau Mastercard simule.
 *
 * Ce module est SERVEUR : il ne fait plus de sign-on, c'est le membre
 * qui l'initie (POST /api/admin/dmas/network/signon sur le port 8084).
 *
 * En revanche, le reseau PEUT emettre vers le membre sur la liaison
 * permanente, une fois celle-ci etablie :
 *   - messages de gestion 0800 (echange de cles system-generated,
 *     cutover, echo)
 *   - advices 0620, reversals 0420
 *   - 0100 / 0200 selon les scenarios
 *
 * Ces endpoints exposent cette capacite. La construction des messages
 * financiers viendra avec la logique metier correspondante.
 */
@RestController
@RequestMapping("/api/admin/dmas/jpos")
public class JposNetworkController {

    private final McDmasMastercardServer server;
    private final McPackagerEbcdic packager = new McPackagerEbcdic();
    private final AtomicInteger stanSeq = new AtomicInteger(900001);

    public JposNetworkController(McDmasMastercardServer server) {
        this.server = server;
    }

    /** Etat de la liaison avec le membre. */
    @GetMapping("/status")
    public ResponseEntity<?> status() {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("role",            "SERVER");
        r.put("session_active",  server.hasActiveSession());
        r.put("member_group_id", server.getActiveMemberGroupId());
        r.put("note", "Le sign-on est initie par le membre (port 8084)");
        return ResponseEntity.ok(r);
    }

    /**
     * Pousse un message de gestion reseau 0800 vers le membre et attend
     * son 0810.
     *
     * Exemples :
     *   POST /api/admin/dmas/jpos/push/network?de70=270   echo vers le membre
     *   POST /api/admin/dmas/jpos/push/network?de70=161   echange de cle
     *
     * @param de70 code de gestion reseau
     * @param wait true pour attendre la reponse, false pour emettre seulement
     */
    @PostMapping("/push/network")
    public ResponseEntity<?> pushNetwork(@RequestParam String de70,
                                         @RequestParam(defaultValue = "true") boolean wait,
                                         @RequestParam(required = false) String de48) {
        try {
            if (!server.hasActiveSession()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "Pas de session membre active — le membre doit faire un sign-on"));
            }

            String stan = String.format("%06d", stanSeq.getAndIncrement() % 1_000_000);
            ISOMsg m = new ISOMsg();
            m.setPackager(packager);
            m.setMTI("0800");
            m.set(2,  server.getActiveMemberGroupId());
            m.set(7,  new SimpleDateFormat("MMddHHmmss").format(new Date()));
            m.set(11, stan);
            m.set(33, "011901");
            if (de48 != null && !de48.isBlank()) m.set(48, de48);
            m.set(70, de70);

            Map<String, Object> r = new LinkedHashMap<>();
            r.put("mti_sent", "0800");
            r.put("de070",    de70);
            r.put("stan",     stan);

            if (wait) {
                ISOMsg resp = server.pushAndWait(m, 15);
                String rc = resp.hasField(39) ? resp.getString(39) : "??";
                r.put("mti_received", resp.getMTI());
                r.put("de039",        rc);
                r.put("success",      "00".equals(rc));
            } else {
                server.pushOnActiveSession(m);
                r.put("success", true);
                r.put("note",    "emis sans attente de reponse");
            }
            return ResponseEntity.ok(r);

        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Pousse un advice 0820 vers le membre (pas de reponse attendue).
     * Utilise notamment pour l'acquittement d'echange de cles.
     */
    @PostMapping("/push/advice")
    public ResponseEntity<?> pushAdvice(@RequestParam String de70,
                                        @RequestParam(required = false) String de48) {
        try {
            if (!server.hasActiveSession()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "Pas de session membre active"));
            }

            String stan = String.format("%06d", stanSeq.getAndIncrement() % 1_000_000);
            ISOMsg m = new ISOMsg();
            m.setPackager(packager);
            m.setMTI("0820");
            m.set(2,  server.getActiveMemberGroupId());
            m.set(7,  new SimpleDateFormat("MMddHHmmss").format(new Date()));
            m.set(11, stan);
            m.set(33, "011901");
            if (de48 != null && !de48.isBlank()) m.set(48, de48);
            m.set(70, de70);

            server.pushOnActiveSession(m);
            return ResponseEntity.ok(Map.of(
                    "mti_sent", "0820",
                    "de070",    de70,
                    "stan",     stan,
                    "success",  true));

        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }
}
