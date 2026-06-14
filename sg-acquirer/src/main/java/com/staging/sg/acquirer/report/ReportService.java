package com.staging.sg.acquirer.report;

import com.staging.sg.acquirer.tps.TpsMetrics;
import com.staging.sg.common.entity.Execution;
import com.staging.sg.common.entity.Result;
import com.staging.sg.common.repository.ExecutionRepository;
import com.staging.sg.common.repository.ResultRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class ReportService {

    private static final Logger log = LoggerFactory.getLogger(ReportService.class);

    private final ExecutionRepository executionRepository;
    private final ResultRepository    resultRepository;

    @Value("${reports.base-dir:D:/MoneyCore/ScenarioGenerator/reports}")
    private String baseDir;

    public ReportService(ExecutionRepository executionRepository,
                         ResultRepository resultRepository) {
        this.executionRepository = executionRepository;
        this.resultRepository    = resultRepository;
    }

    // ── Générer répertoire rapport ────────────────────────────

    public Path createReportDir(Long executionId, String testName) {
        String timestamp = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String dirName = testName.replaceAll("[^a-zA-Z0-9_]", "_")
                + "_" + executionId + "_" + timestamp;
        Path dir = Paths.get(baseDir, dirName);
        try {
            Files.createDirectories(dir);
            log.info("[REPORT] Directory created : {}", dir);
        } catch (Exception e) {
            log.error("[REPORT] Error creating directory : {}", e.getMessage());
        }
        return dir;
    }

    // ── Rapport TXT depuis mémoire ───────────────────────────

    public void generateTxtReport(Long executionId, String testName, TpsMetrics metrics) {
        try {
            Path dir = createReportDir(executionId, testName);
            Path file = dir.resolve("rapport_" + executionId + ".txt");

            try (PrintWriter pw = new PrintWriter(new FileWriter(file.toFile()))) {
                pw.println("═══════════════════════════════════════════════════");
                pw.println("  ScenarioGenerator — Rapport TPS");
                pw.println("═══════════════════════════════════════════════════");
                pw.println("  Test      : " + testName);
                pw.println("  Execution : " + executionId);
                pw.println("  Date      : " + LocalDateTime.now()
                        .format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")));
                pw.println("  Status    : " + metrics.getStatus());
                pw.println("═══════════════════════════════════════════════════");
                pw.println();
                pw.println("  RÉSUMÉ KPIs");
                pw.println("  ─────────────────────────────────────────────────");
                pw.printf("  TX Total    : %d%n",    metrics.getTxTotal());
                pw.printf("  Approuvées  : %d (%.1f%%)%n",
                        metrics.getTxApproved(), metrics.getApprovalRate());
                pw.printf("  Refusées    : %d%n",    metrics.getTxDeclined());
                pw.println();
                pw.printf("  TPS Moyen   : %.1f /s%n", metrics.getAvgTps());
                pw.println();
                pw.printf("  Réponse Avg : %.0f ms%n", metrics.getAvgResponseMs());
                pw.printf("  Réponse Min : %d ms%n",   metrics.getMinResponseMs());
                pw.printf("  Réponse Max : %d ms%n",   metrics.getMaxResponseMs());
                pw.printf("  P95         : %.0f ms%n", metrics.getP95ResponseMs());
                pw.printf("  P99         : %.0f ms%n", metrics.getP99ResponseMs());
                pw.println();

                // Détail paliers
                if (!metrics.getStepRecords().isEmpty()) {
                    pw.println("  PALIERS TPS");
                    pw.println("  ─────────────────────────────────────────────────");
                    pw.printf("  %-8s %-8s %-8s %-10s %-10s%n",
                            "Palier", "Début", "Fin", "TPS cible", "TX envoyés");
                    for (TpsMetrics.StepRecord s : metrics.getStepRecords()) {
                        pw.printf("  %-8d %-8d %-8d %-10d %-10d%n",
                                s.stepOrder, s.startSeconds, s.endSeconds,
                                s.tpsTarget, s.txSent);
                    }
                    pw.println();
                }

                // Détail transactions (max 100)
                pw.println("  DÉTAIL TRANSACTIONS (100 premières)");
                pw.println("  ─────────────────────────────────────────────────");
                pw.printf("  %-5s %-20s %-6s %-8s %-8s%n",
                        "#", "PAN", "DE039", "Auth", "Ms");
                int count = 0;
                for (TpsMetrics.TxRecord r : metrics.getTxRecords()) {
                    if (count++ >= 100) break;
                    pw.printf("  %-5d %-20s %-6s %-8s %-8d%n",
                            count,
                            r.panMasked != null ? r.panMasked : "N/A",
                            r.de039 != null ? r.de039 : "N/A",
                            r.de038AuthCode != null ? r.de038AuthCode : "N/A",
                            r.durationMs);
                }
                pw.println();
                pw.println("═══════════════════════════════════════════════════");
            }

            // Update execution with report path
            executionRepository.findById(executionId).ifPresent(exec -> {
                exec.setReportDir(dir.toString());
                exec.setReportPdf(null);
                exec.setReportExcel(null);
                executionRepository.save(exec);
            });

            log.info("[REPORT] TXT generated : {}", file);

        } catch (Exception e) {
            log.error("[REPORT] Error generating TXT : {}", e.getMessage(), e);
        }
    }

    // ── Rapport PDF depuis base ───────────────────────────────

    public void generatePdfReport(Long executionId) {
        try {
            Execution exec = executionRepository.findById(executionId).orElse(null);
            if (exec == null) return;

            List<Result> results = resultRepository.findByExecutionId(executionId);
            log.info("[REPORT] Generating PDF — execution={} results={}",
                    executionId, results.size());

            Path dir = Paths.get(exec.getReportDir() != null
                    ? exec.getReportDir()
                    : baseDir);
            Files.createDirectories(dir);

            Path pdfFile = dir.resolve("rapport_" + executionId + ".pdf");

            // PDF generation with OpenPDF
            PdfReportGenerator.generate(pdfFile, exec, results);

            exec.setReportPdf(pdfFile.toString());
            executionRepository.save(exec);

            log.info("[REPORT] PDF generated : {}", pdfFile);

        } catch (Exception e) {
            log.error("[REPORT] Error generating PDF : {}", e.getMessage(), e);
        }
    }

    // ── Rapport Excel depuis base ─────────────────────────────

    public void generateExcelReport(Long executionId) {
        try {
            Execution exec = executionRepository.findById(executionId).orElse(null);
            if (exec == null) return;

            List<Result> results = resultRepository.findByExecutionId(executionId);
            log.info("[REPORT] Generating Excel — execution={} results={}",
                    executionId, results.size());

            Path dir = Paths.get(exec.getReportDir() != null
                    ? exec.getReportDir()
                    : baseDir);
            Files.createDirectories(dir);

            Path excelFile = dir.resolve("rapport_" + executionId + ".xlsx");

            // Excel generation with Apache POI
            ExcelReportGenerator.generate(excelFile, exec, results);

            exec.setReportExcel(excelFile.toString());
            executionRepository.save(exec);

            log.info("[REPORT] Excel generated : {}", excelFile);

        } catch (Exception e) {
            log.error("[REPORT] Error generating Excel : {}", e.getMessage(), e);
        }
    }

    public String getBaseDir() { return baseDir; }
}
