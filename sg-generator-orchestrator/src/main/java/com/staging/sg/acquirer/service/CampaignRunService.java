package com.staging.sg.acquirer.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.staging.sg.acquirer.orchestration.DmasClient;
import com.staging.sg.acquirer.report.ExcelReportGenerator;
import com.staging.sg.acquirer.report.PdfReportGenerator;
import com.staging.sg.common.entity.*;
import com.staging.sg.common.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Exécute une CAMPAGNE de test de charge :
 *  - lit la Campagne + TOUS ses paliers (campaign_load_steps)
 *  - cree une CampaignExecution
 *  - joue chaque palier sequentiellement via le moteur de charge de l'acquereur
 *    (POST /loadtest sur la connexion permanente jPOS)
 *  - agrege les resultats de tous les paliers
 *  - calcule le VERDICT SLA (PASSED/FAILED)
 *  - persiste CampaignExecution + CampaignExecutionResult
 *  - genere les rapports Excel/PDF (reutilise les generateurs existants via conversion)
 */
@Service
public class CampaignRunService {

    private static final Logger log = LoggerFactory.getLogger(CampaignRunService.class);

    private final CampaignRepository campaignRepo;
    private final CampaignLoadStepRepository stepRepo;
    private final CampaignExecutionRepository execRepo;
    private final CampaignExecutionResultRepository resultRepo;
    private final UserRepository userRepo;
    private final DmasClient dmasClient;
    private final DmasCardRepository cardRepo;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${dmas.acquirer.base-url:http://localhost:8084}") private String acquirerUrl;
    @Value("${dmas.login:admin}")        private String dmasLogin;
    @Value("${dmas.password:Admin123!}") private String dmasPassword;
    @Value("${dmas.loadtest.max-concurrency:200}") private int maxConcurrency;
    @Value("${dmas.loadtest.report-dir:reports}")  private String reportBaseDir;
    @Value("${dmas.loadtest.poll-interval-ms:1000}") private long pollIntervalMs;

    public CampaignRunService(CampaignRepository campaignRepo,
                              CampaignLoadStepRepository stepRepo,
                              CampaignExecutionRepository execRepo,
                              CampaignExecutionResultRepository resultRepo,
                              UserRepository userRepo,
                              DmasClient dmasClient,
                              DmasCardRepository cardRepo) {
        this.campaignRepo = campaignRepo;
        this.stepRepo = stepRepo;
        this.execRepo = execRepo;
        this.resultRepo = resultRepo;
        this.userRepo = userRepo;
        this.dmasClient = dmasClient;
        this.cardRepo = cardRepo;
    }

    public Map<String,Object> run(Long campaignId, String userLogin) {
        Campaign campaign = campaignRepo.findById(campaignId)
                .orElseThrow(() -> new RuntimeException("Campagne introuvable : " + campaignId));
        List<CampaignLoadStep> steps = stepRepo.findByCampaignIdOrderByStepOrderAsc(campaignId);
        if (steps.isEmpty())
            throw new RuntimeException("La campagne " + campaignId + " n'a aucun palier de charge");
        User user = userRepo.findByLogin(userLogin)
                .orElseThrow(() -> new RuntimeException("Utilisateur introuvable : " + userLogin));

        // Parametrage message depuis le config JSON
        String pan = "0000000000000000", amount = "000000000000", entryMode = "CARD_PRESENT";
        try {
            if (campaign.getConfig() != null && !campaign.getConfig().isBlank()) {
                Map<?,?> cfg = objectMapper.readValue(campaign.getConfig(), Map.class);
                if (cfg.get("DE002_PAN") != null) pan = String.valueOf(cfg.get("DE002_PAN"));
                if (cfg.get("DE004_AMOUNT") != null) {
                    long amt = ((Number) cfg.get("DE004_AMOUNT")).longValue();
                    amount = String.format("%012d", amt);
                }
                if (cfg.get("ENTRY_MODE") != null) entryMode = String.valueOf(cfg.get("ENTRY_MODE"));
            }
        } catch (Exception e) {
            log.warn("[CAMPAIGN] parse config : {}", e.getMessage());
        }

        // Duree totale = somme des paliers ; tps cible affiche = max des paliers
        int totalDuration = 0, maxTps = 0;
        for (CampaignLoadStep s : steps) {
            totalDuration += Math.max(0, (s.getEndSeconds() - s.getStartSeconds()));
            maxTps = Math.max(maxTps, s.getTpsValue() != null ? s.getTpsValue() : 0);
        }

        CampaignExecution exec = new CampaignExecution();
        exec.setCampaign(campaign);
        exec.setUser(user);
        exec.setStatus("RUNNING");
        exec.setTpsTarget(maxTps);
        exec.setDurationSeconds(totalDuration);
        exec.setStartedAt(LocalDateTime.now());
        exec = execRepo.save(exec);

        final Long execId = exec.getId();
        final String campaignName = campaign.getName();
        final String fPan = pan, fAmount = amount, fEntry = entryMode;

        Thread worker = new Thread(() ->
                execute(execId, campaign, campaignName, steps, fPan, fAmount, fEntry),
                "campaign-run-" + execId);
        worker.setDaemon(true);
        worker.start();

        Map<String,Object> r = new LinkedHashMap<>();
        r.put("campaignExecutionId", execId);
        r.put("campaignId", campaignId);
        r.put("campaignName", campaignName);
        r.put("status", "RUNNING");
        r.put("steps", steps.size());
        r.put("tpsTarget", maxTps);
        r.put("durationSeconds", totalDuration);
        return r;
    }

    private void execute(Long execId, Campaign campaign, String campaignName,
                         List<CampaignLoadStep> steps, String pan, String amount, String entryMode) {
        try {
            String token = dmasClient.ensureToken(acquirerUrl, dmasLogin, dmasPassword);
            List<CampaignExecutionResult> allResults = new ArrayList<>();
            CampaignExecution exec = execRepo.findById(execId).orElseThrow();

            // v1.1.0 : mode carte (RANDOM) + PIN depuis le config
            boolean randomCards = false, withPin = false;
            Long amountMin = null, amountMax = null;
            try {
                if (campaign.getConfig() != null && !campaign.getConfig().isBlank()) {
                    Map<?,?> cfg = objectMapper.readValue(campaign.getConfig(), Map.class);
                    randomCards = "RANDOM".equalsIgnoreCase(String.valueOf(cfg.get("DE002_PAN_MODE")));
                    withPin = Boolean.TRUE.equals(cfg.get("WITH_PIN"));
                    // VARIABLE_FIELDS.AMOUNT { mode: RANGE, min, max } -> montant variable par transaction
                    Object vf = cfg.get("VARIABLE_FIELDS");
                    if (vf instanceof Map<?,?> vfm) {
                        Object am = vfm.get("AMOUNT");
                        if (am instanceof Map<?,?> amm && "RANGE".equalsIgnoreCase(String.valueOf(amm.get("mode")))) {
                            if (amm.get("min") != null) amountMin = ((Number) amm.get("min")).longValue();
                            if (amm.get("max") != null) amountMax = ((Number) amm.get("max")).longValue();
                        }
                    }
                }
            } catch (Exception ce) { log.warn("[CAMPAIGN] parse config (cartes) : {}", ce.getMessage()); }
            List<Map<String,String>> cardPool = new ArrayList<>();
            if (randomCards) {
                cardPool = cardRepo.findAll().stream()
                    .filter(c -> "ACTIVE".equals(c.getStatus()) && c.getBalance() != null && c.getBalance() > 0)
                    .map(c -> { Map<String,String> m = new LinkedHashMap<>(); m.put("pan", c.getPan()); m.put("pin", c.getPin()); return m; })
                    .collect(java.util.stream.Collectors.toList());
                log.info("[CAMPAIGN] exec={} mode RANDOM : {} cartes dans le pool, withPin={}", execId, cardPool.size(), withPin);
                if (cardPool.isEmpty()) throw new RuntimeException("Mode RANDOM mais aucune carte ACTIVE avec solde dans dmas_cards");
            }
            final boolean fWithPin = withPin;
            final List<Map<String,String>> fCardPool = cardPool;
            final Long fAmountMin = amountMin, fAmountMax = amountMax;

            // ===== Jouer CHAQUE palier sequentiellement =====
            boolean breakerTripped = false;
            String breakerDetail = null;
            for (CampaignLoadStep step : steps) {
                int tps = step.getTpsValue() != null ? step.getTpsValue() : 1;
                int dur = Math.max(1, step.getEndSeconds() - step.getStartSeconds());
                int conc = step.getConcurrency() != null ? step.getConcurrency()
                        : Math.min(tps, maxConcurrency);

                log.info("[CAMPAIGN] exec={} palier {} : {} TPS / {}s / conc={}",
                        execId, step.getStepOrder(), tps, dur, conc);

                Map<String,Object> body = new LinkedHashMap<>();
                body.put("pan", pan);
                body.put("amount", amount);
                body.put("entryMode", entryMode);
                body.put("durationSeconds", dur);
                body.put("targetTps", tps);
                body.put("concurrency", conc);
                body.put("withPin", fWithPin);
                if (!fCardPool.isEmpty()) body.put("cards", fCardPool);
                if (fAmountMin != null && fAmountMax != null) {
                    body.put("amountMin", fAmountMin);
                    body.put("amountMax", fAmountMax);
                }
                String resp = dmasClient.postJson(acquirerUrl, "/api/admin/dmas/loadtest", token, body);
                Map<?,?> startJson = dmasClient.parse(resp);
                String loadTestId = String.valueOf(startJson.get("loadTestId"));
                if (loadTestId == null || "null".equals(loadTestId)) {
                    log.warn("[CAMPAIGN] exec={} palier {} refuse : {}", execId, step.getStepOrder(), resp);
                    continue;
                }

                // Poll ce palier jusqu'a COMPLETED
                long deadline = System.currentTimeMillis() + (dur + 60L) * 1000L;
                Map<?,?> status = null;
                while (System.currentTimeMillis() < deadline) {
                    Thread.sleep(pollIntervalMs);
                    String s = dmasClient.getJson(acquirerUrl,
                            "/api/admin/dmas/loadtest/" + loadTestId + "/status?details=true", token);
                    status = dmasClient.parse(s);
                    String st = String.valueOf(status.get("status"));
                    if ("COMPLETED".equals(st) || "ERROR".equals(st)) break;
                }

                // Collecter les details de ce palier
                Object detailsObj = status != null ? status.get("details") : null;
                if (detailsObj instanceof List<?> list) {
                    for (Object o : list) {
                        if (!(o instanceof Map<?,?> d)) continue;
                        CampaignExecutionResult cr = new CampaignExecutionResult();
                        cr.setExecution(exec);
                        cr.setStepOrder(step.getStepOrder());
                        String txPan = d.get("pan") != null ? String.valueOf(d.get("pan")) : pan;
                        cr.setPanMasked(maskPan(txPan));
                        String de39 = String.valueOf(d.get("de39"));
                        cr.setDe039(de39 != null && de39.length() > 2 ? de39.substring(0,2) : de39);
                        cr.setApproved(Boolean.TRUE.equals(d.get("approved")));
                        long dms = d.get("durationMs") != null ? ((Number) d.get("durationMs")).longValue() : 0;
                        cr.setDurationMs((int) dms);
                        if (d.get("requestHex") != null)  cr.setRequestHex(String.valueOf(d.get("requestHex")));
                        if (d.get("responseHex") != null) cr.setResponseHex(String.valueOf(d.get("responseHex")));
                        cr.setExecutedAt(LocalDateTime.now());
                        allResults.add(cr);
                    }
                }
                // Circuit breaker : seuil defini + taux d erreur cumule depasse -> on arrete
                if (campaign.getStopOnErrorRate() != null && !allResults.isEmpty()) {
                    long dec = allResults.stream().filter(r -> !Boolean.TRUE.equals(r.getApproved())).count();
                    double curErr = dec * 100.0 / allResults.size();
                    if (curErr > campaign.getStopOnErrorRate().doubleValue()) {
                        breakerDetail = String.format("Circuit breaker: taux err %.1f%% > seuil %s%% apres palier %d",
                                curErr, campaign.getStopOnErrorRate(), step.getStepOrder());
                        log.warn("[CAMPAIGN] exec={} {} — arret", execId, breakerDetail);
                        breakerTripped = true;
                        break;
                    }
                }
            }

            // ===== Agreger sur TOUS les paliers =====
            int total = allResults.size();
            int approved = 0, declined = 0;
            long sumMs = 0, minMs = Long.MAX_VALUE, maxMs = 0;
            List<Long> lat = new ArrayList<>();
            for (CampaignExecutionResult cr : allResults) {
                boolean ok = Boolean.TRUE.equals(cr.getApproved());
                if (ok) approved++; else declined++;
                long d = cr.getDurationMs() != null ? cr.getDurationMs() : 0;
                sumMs += d; minMs = Math.min(minMs, d); maxMs = Math.max(maxMs, d);
                lat.add(d);
            }
            Collections.sort(lat);

            exec.setTxTotal(total);
            exec.setTxSent(total);
            exec.setTxApproved(approved);
            exec.setTxDeclined(declined);
            if (total > 0) {
                exec.setResponseTimeAvg(BigDecimal.valueOf(sumMs / (double) total).setScale(2, RoundingMode.HALF_UP));
                exec.setResponseTimeMin(BigDecimal.valueOf(minMs == Long.MAX_VALUE ? 0 : minMs));
                exec.setResponseTimeMax(BigDecimal.valueOf(maxMs));
                exec.setResponseTimeP95(BigDecimal.valueOf(percentile(lat, 95)));
                exec.setResponseTimeP99(BigDecimal.valueOf(percentile(lat, 99)));
            }
            int totalDur = exec.getDurationSeconds() != null ? exec.getDurationSeconds() : 1;
            exec.setTpsActualAvg(BigDecimal.valueOf(totalDur > 0 ? total / (double) totalDur : 0)
                    .setScale(2, RoundingMode.HALF_UP));
            exec.setEndedAt(LocalDateTime.now());
            exec.setStatus(breakerTripped ? "STOPPED_ERROR_RATE" : "COMPLETED");

            // ===== VERDICT SLA =====
            computeVerdict(exec, campaign, total, approved, declined);
            if (breakerTripped && breakerDetail != null) exec.setVerdictDetail(breakerDetail);

            execRepo.save(exec);
            if (!allResults.isEmpty()) resultRepo.saveAll(allResults);

            // ===== Rapports (reutilise generateurs Test via conversion) =====
            try {
                Path dir = Paths.get(reportBaseDir);
                Files.createDirectories(dir);
                Path xlsx = dir.resolve("campaign-" + execId + ".xlsx");
                Path pdf  = dir.resolve("campaign-" + execId + ".pdf");
                Execution tExec = toTransportExecution(exec);
                List<Result> tResults = toTransportResults(allResults);
                String label = campaignName + (exec.getVerdict() != null ? "  [" + exec.getVerdict() + "]" : "");
                ExcelReportGenerator.generate(xlsx, tExec, label, tResults);
                PdfReportGenerator.generate(pdf, tExec, label, tResults);
                exec.setReportDir(dir.toAbsolutePath().toString());
                exec.setReportExcel(xlsx.toAbsolutePath().toString());
                exec.setReportPdf(pdf.toAbsolutePath().toString());
                execRepo.save(exec);
                log.info("[CAMPAIGN] exec={} rapports generes", execId);
            } catch (Exception re) {
                log.error("[CAMPAIGN] exec={} erreur rapports : {}", execId, re.getMessage());
            }

            log.info("[CAMPAIGN] exec={} COMPLETED total={} approved={} declined={} verdict={}",
                    execId, total, approved, declined, exec.getVerdict());
        } catch (Exception e) {
            log.error("[CAMPAIGN] exec={} erreur : {}", execId, e.getMessage(), e);
            execRepo.findById(execId).ifPresent(ex -> {
                ex.setStatus("ERROR");
                ex.setEndedAt(LocalDateTime.now());
                execRepo.save(ex);
            });
        }
    }

    /** Calcule le verdict PASSED/FAILED selon les SLA de la campagne. */
    private void computeVerdict(CampaignExecution exec, Campaign c, int total, int approved, int declined) {
        if (c.getSlaP95MaxMs() == null && c.getSlaErrorRateMax() == null && c.getSlaApprovalMin() == null) {
            exec.setVerdict(null);  // pas de SLA defini
            exec.setVerdictDetail("Aucun SLA defini");
            return;
        }
        List<String> failures = new ArrayList<>();
        // p95
        if (c.getSlaP95MaxMs() != null && exec.getResponseTimeP95() != null
                && exec.getResponseTimeP95().doubleValue() > c.getSlaP95MaxMs()) {
            failures.add("p95=" + exec.getResponseTimeP95() + "ms > " + c.getSlaP95MaxMs() + "ms");
        }
        // taux erreur (declined+error / total)
        double errorRate = total > 0 ? (declined * 100.0 / total) : 0;
        if (c.getSlaErrorRateMax() != null && errorRate > c.getSlaErrorRateMax().doubleValue()) {
            failures.add(String.format("erreurs=%.1f%% > %s%%", errorRate, c.getSlaErrorRateMax()));
        }
        // taux approbation
        double approvalRate = total > 0 ? (approved * 100.0 / total) : 0;
        if (c.getSlaApprovalMin() != null && approvalRate < c.getSlaApprovalMin().doubleValue()) {
            failures.add(String.format("approbation=%.1f%% < %s%%", approvalRate, c.getSlaApprovalMin()));
        }
        if (failures.isEmpty()) {
            exec.setVerdict("PASSED");
            exec.setVerdictDetail("Tous les criteres SLA respectes");
        } else {
            exec.setVerdict("FAILED");
            exec.setVerdictDetail(String.join(" ; ", failures));
        }
    }

    // ----- conversion vers types Execution/Result pour reutiliser les rapports -----
    private Execution toTransportExecution(CampaignExecution ce) {
        Execution e = new Execution();
        e.setId(ce.getId());
        e.setMode(ExecutionMode.CHARGE);
        e.setStatus(ExecutionStatus.COMPLETED);
        e.setTpsActualAvg(ce.getTpsActualAvg());
        e.setResponseTimeAvg(ce.getResponseTimeAvg());
        e.setResponseTimeMin(ce.getResponseTimeMin());
        e.setResponseTimeMax(ce.getResponseTimeMax());
        e.setResponseTimeP95(ce.getResponseTimeP95());
        e.setResponseTimeP99(ce.getResponseTimeP99());
        e.setStartedAt(ce.getStartedAt());
        e.setEndedAt(ce.getEndedAt());
        return e;
    }

    private List<Result> toTransportResults(List<CampaignExecutionResult> crs) {
        List<Result> out = new ArrayList<>(crs.size());
        for (CampaignExecutionResult cr : crs) {
            Result r = new Result();
            r.setPanMasked(cr.getPanMasked());
            r.setDe039(cr.getDe039());
            r.setDe038AuthCode(cr.getDe038AuthCode());
            r.setApproved(cr.getApproved());
            r.setDurationMs(cr.getDurationMs());
            out.add(r);
        }
        return out;
    }

    private static double percentile(List<Long> sorted, int p) {
        if (sorted.isEmpty()) return 0;
        int idx = (int) Math.ceil(p / 100.0 * sorted.size()) - 1;
        idx = Math.max(0, Math.min(idx, sorted.size() - 1));
        return sorted.get(idx);
    }

    private static String maskPan(String pan) {
        if (pan == null || pan.length() < 10) return pan;
        return pan.substring(0, 6) + "******" + pan.substring(pan.length() - 4);
    }
}
