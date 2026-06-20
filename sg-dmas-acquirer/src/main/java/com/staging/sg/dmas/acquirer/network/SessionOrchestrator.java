package com.staging.sg.dmas.acquirer.network;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Orchestrateur de session DMAS côté RÉSEAU (acquéreur).
 *
 * Enchaîne automatiquement la séquence d'établissement de session avec
 * la banque émettrice (issuer) :
 *   1. Sign-on        (0800 DE70=001 -> 0810)
 *   2. PEK exchange   (0800 DE70=161 -> 0810)
 *   3. Echo test      (0800 DE70=270 -> 0810)
 *
 * Déclenché :
 *   - automatiquement au démarrage (ApplicationReadyEvent) si dmas.session.auto-start=true
 *   - manuellement via l'endpoint POST /api/admin/dmas/session/start
 */
@Service
public class SessionOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(SessionOrchestrator.class);

    private final McDmasNetworkManager network;
    private final McDmasKeyExchange    keyExchange;

    @Value("${dmas.member-group-id:TESTGRP01}") private String memberGroupId;
    @Value("${dmas.session.auto-start:false}")  private boolean autoStart;
    @Value("${dmas.session.startup-delay-ms:3000}") private long startupDelayMs;

    public SessionOrchestrator(McDmasNetworkManager network, McDmasKeyExchange keyExchange) {
        this.network = network;
        this.keyExchange = keyExchange;
    }

    /** Démarrage automatique à la fin du boot, si activé. */
    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        if (!autoStart) {
            log.info("[DMAS-SESSION] auto-start désactivé (dmas.session.auto-start=false)");
            return;
        }
        // Laisser le temps à l'issuer d'être prêt (socket)
        new Thread(() -> {
            try {
                Thread.sleep(startupDelayMs);
                log.info("[DMAS-SESSION] Démarrage automatique de la session...");
                startSession();
            } catch (Exception e) {
                log.error("[DMAS-SESSION] Échec démarrage auto : {}", e.getMessage(), e);
            }
        }, "dmas-session-autostart").start();
    }

    /**
     * Enchaîne sign-on -> key exchange -> echo.
     * Chaque étape doit réussir (DE39=00) pour passer à la suivante.
     */
    public Map<String,Object> startSession() throws Exception {
        Map<String,Object> result = new LinkedHashMap<>();
        log.info("[DMAS-SESSION] ========== DÉBUT SÉQUENCE SESSION ==========");

        // 1. SIGN-ON
        log.info("[DMAS-SESSION] Étape 1/3 : SIGN-ON (DE70=001)");
        Map<String,Object> signon = network.sendSignOn();
        result.put("signon", signon);
        if (!isOk(signon)) {
            log.warn("[DMAS-SESSION] Sign-on échoué -> arrêt séquence");
            result.put("status", "FAILED_AT_SIGNON");
            return result;
        }

        // 2. PEK EXCHANGE
        log.info("[DMAS-SESSION] Étape 2/3 : PEK EXCHANGE (DE70=161)");
        Map<String,Object> pek = keyExchange.exchangePek(memberGroupId);
        result.put("key_exchange", pek);
        if (!isOk(pek)) {
            log.warn("[DMAS-SESSION] PEK exchange échoué -> arrêt séquence");
            result.put("status", "FAILED_AT_KEYEXCHANGE");
            return result;
        }

        // 3. ECHO TEST
        log.info("[DMAS-SESSION] Étape 3/3 : ECHO TEST (DE70=270)");
        Map<String,Object> echo = network.sendEcho();
        result.put("echo", echo);
        if (!isOk(echo)) {
            log.warn("[DMAS-SESSION] Echo échoué -> session établie mais echo KO");
            result.put("status", "SESSION_UP_ECHO_FAILED");
            return result;
        }

        log.info("[DMAS-SESSION] ========== SESSION ÉTABLIE ET ACTIVE ==========");
        result.put("status", "SESSION_ACTIVE");
        return result;
    }

    /** Ferme la session proprement (sign-off). */
    public Map<String,Object> stopSession() throws Exception {
        log.info("[DMAS-SESSION] Fermeture session : SIGN-OFF (DE70=002)");
        Map<String,Object> signoff = network.sendSignOff();
        Map<String,Object> result = new LinkedHashMap<>();
        result.put("signoff", signoff);
        result.put("status", isOk(signoff) ? "SESSION_CLOSED" : "SIGNOFF_FAILED");
        return result;
    }

    @SuppressWarnings("unchecked")
    private boolean isOk(Map<String,Object> m) {
        Object ok = m.get("success");
        if (ok instanceof Boolean) return (Boolean) ok;
        Object de39 = m.get("de039");
        return "00".equals(de39);
    }
}
