package com.staging.sg.acquirer.orchestration;

import com.staging.sg.common.entity.*;
import com.staging.sg.common.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Persiste les résultats d'un rejeu de campagne APRÈS sa fin (pas pendant).
 * Pilotée par flags YAML :
 *   orchestration.persist.dashboard -> agrégat + répartition DE39 (table dédiée)
 *   orchestration.persist.details   -> détail transaction par transaction
 */
@Service
public class ExecutionPersistenceService {

    private static final Logger log = LoggerFactory.getLogger(ExecutionPersistenceService.class);

    private final CampaignExecutionRepository execRepo;
    private final CampaignExecutionDe39StatRepository de39Repo;
    private final CampaignExecutionResultRepository resultRepo;

    @Value("${orchestration.persist.dashboard:true}") private boolean persistDashboard;
    @Value("${orchestration.persist.details:false}")  private boolean persistDetails;

    public ExecutionPersistenceService(CampaignExecutionRepository execRepo,
                                       CampaignExecutionDe39StatRepository de39Repo,
                                       CampaignExecutionResultRepository resultRepo) {
        this.execRepo = execRepo;
        this.de39Repo = de39Repo;
        this.resultRepo = resultRepo;
    }

    /** Une ligne de résultat en mémoire (produite par le rejeu). */
    public static class TxResult {
        public final String panMasked;
        public final String de39;
        public final boolean approved;
        public final long durationMs;
        public final boolean error;
        public TxResult(String panMasked, String de39, boolean approved, long durationMs, boolean error) {
            this.panMasked = panMasked; this.de39 = de39; this.approved = approved;
            this.durationMs = durationMs; this.error = error;
        }
    }

    /** Crée l'exécution (RUNNING) au démarrage du rejeu. */
    public CampaignExecution start(Long campaignId) {
        CampaignExecution e = new CampaignExecution();
        e.setCampaignId(campaignId);
        e.setStatus("RUNNING");
        e.setStartedAt(LocalDateTime.now());
        return execRepo.save(e);
    }

    /** Finalise et persiste les résultats selon les flags. */
    public void finish(CampaignExecution exec, List<TxResult> results) {
        int total = results.size();
        int approved = 0, declined = 0, errors = 0;
        List<Long> latencies = new ArrayList<>();
        Map<String,Integer> de39Counts = new TreeMap<>();

        for (TxResult r : results) {
            if (r.error) { errors++; continue; }
            if (r.approved) approved++; else declined++;
            latencies.add(r.durationMs);
            String key = (r.de39 == null || r.de39.equals("null")) ? "ERR" : r.de39;
            de39Counts.merge(key, 1, Integer::sum);
        }

        // Agrégats latence
        exec.setTxTotal(total);
        exec.setTxApproved(approved);
        exec.setTxDeclined(declined);
        exec.setTxErrors(errors);
        if (!latencies.isEmpty()) {
            Collections.sort(latencies);
            long sum = latencies.stream().mapToLong(Long::longValue).sum();
            exec.setResponseTimeAvg(bd((double) sum / latencies.size()));
            exec.setResponseTimeMin(bd(latencies.get(0)));
            exec.setResponseTimeMax(bd(latencies.get(latencies.size() - 1)));
            exec.setResponseTimeP95(bd(percentile(latencies, 95)));
            exec.setResponseTimeP99(bd(percentile(latencies, 99)));
        }
        exec.setStatus("COMPLETED");
        exec.setEndedAt(LocalDateTime.now());
        execRepo.save(exec);

        // Tableau de bord : répartition DE39 (table dédiée)
        if (persistDashboard) {
            List<CampaignExecutionDe39Stat> stats = new ArrayList<>();
            for (Map.Entry<String,Integer> e : de39Counts.entrySet()) {
                CampaignExecutionDe39Stat s = new CampaignExecutionDe39Stat();
                s.setExecutionId(exec.getId());
                s.setDe39(e.getKey());
                s.setCount(e.getValue());
                stats.add(s);
            }
            de39Repo.saveAll(stats);
            log.info("[PERSIST] Exécution {} : dashboard ({} codes DE39)", exec.getId(), stats.size());
        }

        // Détail (optionnel)
        if (persistDetails) {
            List<CampaignExecutionResult> rows = new ArrayList<>();
            for (TxResult r : results) {
                CampaignExecutionResult cr = new CampaignExecutionResult();
                cr.setExecutionId(exec.getId());
                cr.setPanMasked(r.panMasked);
                cr.setDe39(r.de39);
                cr.setApproved(r.approved);
                cr.setDurationMs((int) r.durationMs);
                rows.add(cr);
            }
            resultRepo.saveAll(rows);
            log.info("[PERSIST] Exécution {} : détail ({} transactions)", exec.getId(), rows.size());
        }
    }

    private double percentile(List<Long> sorted, int p) {
        if (sorted.isEmpty()) return 0;
        int idx = (int) Math.ceil(p / 100.0 * sorted.size()) - 1;
        idx = Math.max(0, Math.min(idx, sorted.size() - 1));
        return sorted.get(idx);
    }

    private BigDecimal bd(double v) {
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP);
    }
}
