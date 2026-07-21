package com.staging.sg.mc.dmas.member.api;

import com.staging.sg.mc.dmas.member.network.McDmasKeyExchange;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Sollicitation d'echange de cle — mecanisme 162.
 *
 *   POST /api/admin/dmas/keys/solicit
 *   POST /api/admin/dmas/keys/solicit?bank=022905
 *
 * En mono-banque, ?bank= peut etre omis. En multi-banque il designe
 * la liaison a utiliser.
 *
 * Envoie un 0800 DE70=162. La cle n'arrive PAS dans la reponse : le
 * reseau la poussera ensuite dans un 0800 DE70=161, traite par le
 * thread listener du client. Consulter ensuite :
 *
 *   GET /api/admin/dmas/keys/current
 *
 * pour voir la cle passer de RECEIVED (livree) a ACTIVE (acquittee
 * par le 0820).
 */
@RestController
@RequestMapping("/api/admin/dmas/keys")
public class McDmasKeySolicitController {

    private final McDmasKeyExchange keyExchange;

    public McDmasKeySolicitController(McDmasKeyExchange keyExchange) {
        this.keyExchange = keyExchange;
    }

    @PostMapping("/solicit")
    public ResponseEntity<?> solicit(
            @RequestParam(required = false) String bank) {
        try {
            return ResponseEntity.ok(keyExchange.solicitPek(bank));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", String.valueOf(e.getMessage())));
        }
    }
}
