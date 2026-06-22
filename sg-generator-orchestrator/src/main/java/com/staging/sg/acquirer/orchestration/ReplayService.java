package com.staging.sg.acquirer.orchestration;

import com.staging.sg.common.entity.CampaignExecution;
import com.staging.sg.common.entity.GeneratedTransaction;
import com.staging.sg.common.repository.GeneratedTransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Rejeu SÉQUENTIEL des transactions d'une campagne contre l'acquéreur DMAS (8084).
 * Garde les résultats en mémoire pendant le rejeu, persiste APRÈS la fin
 * (via ExecutionPersistenceService, selon les flags YAML).
 */
@Service
public class ReplayService {

    private static final Logger log = LoggerFactory.getLogger(ReplayService.class);

    private final GeneratedTransactionRepository txRepo;
    private final DmasClient dmasClient;
    private final ExecutionPersistenceService persistence;

    @Value("${dmas.acquirer.base-url:http://localhost:8084}") private String acquirerUrl;
    @Value("${dmas.login:admin}")        private String login;
    @Value("${dmas.password:Admin123!}") private String password;

    public ReplayService(GeneratedTransactionRepository txRepo,
                         DmasClient dmasClient,
                         ExecutionPersistenceService persistence) {
        this.txRepo = txRepo;
        this.dmasClient = dmasClient;
        this.persistence = persistence;
    }

    public Map<String,Object> replay(Long campaignId) {
        List<GeneratedTransaction> txs = txRepo.findByCampaignId(campaignId);
        if (txs.isEmpty()) {
            throw new IllegalStateException("Aucune transaction pour la campagne " + campaignId);
        }

        // 1. Créer l'exécution (RUNNING)
        CampaignExecution exec = persistence.start(campaignId);

        String token = dmasClient.ensureToken(acquirerUrl, login, password);

        // 2. Rejeu — tout en mémoire (pas de persistance pendant)
        int approved = 0, declined = 0, errors = 0;
        long totalMs = 0;
        List<ExecutionPersistenceService.TxResult> results = new ArrayList<>();
        List<Map<String,Object>> details = new ArrayList<>();

        for (GeneratedTransaction t : txs) {
            Map<String,Object> body = DmasMapper.toAuthBody(t);
            String panMasked = mask(t.getDe2Pan());
            long start = System.currentTimeMillis();
            Map<String,Object> line = new LinkedHashMap<>();
            line.put("pan", panMasked);
            line.put("amount", t.getDe4Amount());
            try {
                String resp = dmasClient.postJson(acquirerUrl, "/api/admin/dmas/auth", token, body);
                long ms = System.currentTimeMillis() - start;
                totalMs += ms;
                Map<?,?> json = dmasClient.parse(resp);
                String de39 = String.valueOf(json.get("de039_response_code"));
                boolean ok = Boolean.TRUE.equals(json.get("approved")) || "00".equals(de39);
                if (ok) approved++; else declined++;
                results.add(new ExecutionPersistenceService.TxResult(panMasked, de39, ok, ms, false));
                line.put("de039", de39);
                line.put("approved", ok);
                line.put("ms", ms);
            } catch (Exception e) {
                errors++;
                results.add(new ExecutionPersistenceService.TxResult(panMasked, null, false, 0, true));
                line.put("error", e.getMessage());
            }
            details.add(line);
        }

        // 3. Persister APRÈS la fin (selon flags YAML)
        persistence.finish(exec, results);

        log.info("[REPLAY] Campagne {} exec {} : {} approuvées, {} refusées, {} erreurs ({} tx)",
                campaignId, exec.getId(), approved, declined, errors, txs.size());

        Map<String,Object> r = new LinkedHashMap<>();
        r.put("campaignId", campaignId);
        r.put("executionId", exec.getId());
        r.put("total", txs.size());
        r.put("approved", approved);
        r.put("declined", declined);
        r.put("errors", errors);
        r.put("avgMs", txs.isEmpty() ? 0 : totalMs / txs.size());
        r.put("details", details);
        return r;
    }

    private String mask(String pan) {
        if (pan == null || pan.length() < 10) return pan;
        return pan.substring(0, 6) + "****" + pan.substring(pan.length() - 4);
    }
}
