package com.staging.sg.acquirer.report;

import com.lowagie.text.*;
import com.lowagie.text.Font;
import com.lowagie.text.pdf.*;
import com.staging.sg.common.entity.Execution;
import com.staging.sg.common.entity.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import com.lowagie.text.Rectangle;
import java.io.FileOutputStream;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class PdfReportGenerator {

    private static final Logger log = LoggerFactory.getLogger(PdfReportGenerator.class);

    private static final Font FONT_TITLE  = new Font(Font.HELVETICA, 16, Font.BOLD, Color.WHITE);
    private static final Font FONT_H2     = new Font(Font.HELVETICA, 12, Font.BOLD, new Color(33, 97, 140));
    private static final Font FONT_NORMAL = new Font(Font.HELVETICA, 9,  Font.NORMAL);
    private static final Font FONT_BOLD   = new Font(Font.HELVETICA, 9,  Font.BOLD);
    private static final Font FONT_HEADER = new Font(Font.HELVETICA, 9,  Font.BOLD, Color.WHITE);

    private static final Color COLOR_PRIMARY = new Color(33, 97, 140);
    private static final Color COLOR_SUCCESS = new Color(39, 174, 96);
    private static final Color COLOR_DANGER  = new Color(231, 76, 60);
    private static final Color COLOR_LIGHT   = new Color(236, 240, 241);

    public static void generate(Path outputPath, Execution exec, String testName, List<Result> results) {
        try {
            Document doc = new Document(PageSize.A4, 36, 36, 54, 36);
            PdfWriter.getInstance(doc, new FileOutputStream(outputPath.toFile()));
            doc.open();

            // ── Page 1 — Résumé ──────────────────────────────
            addTitle(doc, exec, testName);
            addSummary(doc, exec, testName, results);

            doc.newPage();

            // ── Page 2 — Détail transactions ─────────────────
            addTransactionDetail(doc, results);

            doc.close();
            log.info("[PDF] Generated : {}", outputPath);

        } catch (Exception e) {
            log.error("[PDF] Error : {}", e.getMessage(), e);
        }
    }

    private static void addTitle(Document doc, Execution exec, String testName) throws Exception {
        // Header banner
        PdfPTable banner = new PdfPTable(1);
        banner.setWidthPercentage(100);
        PdfPCell cell = new PdfPCell();
        cell.setBackgroundColor(COLOR_PRIMARY);
        cell.setPadding(15);
        cell.setBorder(Rectangle.NO_BORDER);

        Paragraph title = new Paragraph("ScenarioGenerator — Rapport TPS", FONT_TITLE);
        title.setAlignment(Element.ALIGN_CENTER);
        cell.addElement(title);

        Paragraph subtitle = new Paragraph(
                testName,
                new Font(Font.HELVETICA, 11, Font.NORMAL, Color.WHITE));
        subtitle.setAlignment(Element.ALIGN_CENTER);
        cell.addElement(subtitle);
        banner.addCell(cell);
        doc.add(banner);
        doc.add(Chunk.NEWLINE);
    }

    private static void addSummary(Document doc, Execution exec, String testName,
                                    List<Result> results) throws Exception {
        // Info table
        Paragraph h2 = new Paragraph("Informations générales", FONT_H2);
        doc.add(h2);
        doc.add(Chunk.NEWLINE);

        PdfPTable infoTable = new PdfPTable(2);
        infoTable.setWidthPercentage(100);
        infoTable.setWidths(new float[]{1, 2});

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

        addInfoRow(infoTable, "Test",       testName);
        addInfoRow(infoTable, "Execution",  String.valueOf(exec.getId()));
        addInfoRow(infoTable, "Mode",       exec.getMode() != null ? exec.getMode().name() : "N/A");
        addInfoRow(infoTable, "Status",     exec.getStatus() != null ? exec.getStatus().name() : "N/A");
        addInfoRow(infoTable, "Démarré le", exec.getStartedAt() != null ? exec.getStartedAt().format(fmt) : "N/A");
        addInfoRow(infoTable, "Terminé le", exec.getEndedAt() != null ? exec.getEndedAt().format(fmt) : "N/A");
        doc.add(infoTable);
        doc.add(Chunk.NEWLINE);

        // KPIs
        Paragraph h2kpi = new Paragraph("Indicateurs clés", FONT_H2);
        doc.add(h2kpi);
        doc.add(Chunk.NEWLINE);

        long approved = results.stream().filter(r -> Boolean.TRUE.equals(r.getApproved())).count();
        long declined = results.stream().filter(r -> Boolean.FALSE.equals(r.getApproved())).count();
        double approvalRate = results.isEmpty() ? 0 : (double) approved / results.size() * 100;

        PdfPTable kpiTable = new PdfPTable(3);
        kpiTable.setWidthPercentage(100);

        addKpiCell(kpiTable, "TX Total",    String.valueOf(results.size()), COLOR_PRIMARY);
        addKpiCell(kpiTable, "Approuvées",  approved + " (" + String.format("%.1f%%", approvalRate) + ")", COLOR_SUCCESS);
        addKpiCell(kpiTable, "Refusées",    String.valueOf(declined), COLOR_DANGER);
        doc.add(kpiTable);
        doc.add(Chunk.NEWLINE);

        // Response time metrics
        PdfPTable metricsTable = new PdfPTable(2);
        metricsTable.setWidthPercentage(100);
        metricsTable.setWidths(new float[]{1, 2});

        if (exec.getTpsActualAvg() != null)
            addInfoRow(metricsTable, "TPS Moyen",    exec.getTpsActualAvg() + " /s");
        if (exec.getResponseTimeAvg() != null)
            addInfoRow(metricsTable, "Réponse Avg",  exec.getResponseTimeAvg() + " ms");
        if (exec.getResponseTimeMin() != null)
            addInfoRow(metricsTable, "Réponse Min",  exec.getResponseTimeMin() + " ms");
        if (exec.getResponseTimeMax() != null)
            addInfoRow(metricsTable, "Réponse Max",  exec.getResponseTimeMax() + " ms");
        if (exec.getResponseTimeP95() != null)
            addInfoRow(metricsTable, "P95",          exec.getResponseTimeP95() + " ms");
        if (exec.getResponseTimeP99() != null)
            addInfoRow(metricsTable, "P99",          exec.getResponseTimeP99() + " ms");
        doc.add(metricsTable);

        // Codes DE039
        doc.add(Chunk.NEWLINE);
        Paragraph h2de039 = new Paragraph("Distribution codes réponse DE039", FONT_H2);
        doc.add(h2de039);
        doc.add(Chunk.NEWLINE);

        Map<String, Long> de039Map = results.stream()
                .collect(Collectors.groupingBy(
                        r -> r.getDe039() != null ? r.getDe039() : "N/A",
                        Collectors.counting()));

        PdfPTable de039Table = new PdfPTable(3);
        de039Table.setWidthPercentage(60);
        addTableHeader(de039Table, "Code DE039", "Libellé", "Nombre");
        de039Map.forEach((code, count) -> {
            addTableRow(de039Table, code, getDE039Label(code), String.valueOf(count));
        });
        doc.add(de039Table);
    }

    private static void addTransactionDetail(Document doc,
                                              List<Result> results) throws Exception {
        Paragraph h2 = new Paragraph("Détail des transactions", FONT_H2);
        doc.add(h2);
        doc.add(Chunk.NEWLINE);

        PdfPTable table = new PdfPTable(5);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{1, 3, 2, 2, 2});

        addTableHeader(table, "#", "PAN", "DE039", "Auth Code", "Durée (ms)");

        int count = 0;
        for (Result r : results) {
            if (count++ >= 500) break; // max 500 lignes
            boolean alt = count % 2 == 0;
            addTableRowColored(table,
                    String.valueOf(count),
                    r.getPanMasked() != null ? r.getPanMasked() : "N/A",
                    r.getDe039() != null ? r.getDe039() : "N/A",
                    r.getDe038AuthCode() != null ? r.getDe038AuthCode() : "N/A",
                    r.getDurationMs() != null ? r.getDurationMs() + " ms" : "N/A",
                    alt ? COLOR_LIGHT : Color.WHITE);
        }

        doc.add(table);

        if (results.size() > 500) {
            doc.add(new Paragraph("... et " + (results.size() - 500) +
                    " transactions supplémentaires.", FONT_NORMAL));
        }
    }

    // ── Helpers ──────────────────────────────────────────────

    private static void addInfoRow(PdfPTable table, String label, String value) {
        PdfPCell lCell = new PdfPCell(new Phrase(label, FONT_BOLD));
        lCell.setBackgroundColor(COLOR_LIGHT);
        lCell.setPadding(5);
        table.addCell(lCell);

        PdfPCell vCell = new PdfPCell(new Phrase(value, FONT_NORMAL));
        vCell.setPadding(5);
        table.addCell(vCell);
    }

    private static void addKpiCell(PdfPTable table, String label,
                                    String value, Color color) {
        PdfPCell cell = new PdfPCell();
        cell.setBackgroundColor(color);
        cell.setPadding(10);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);

        Paragraph p = new Paragraph(value,
                new Font(Font.HELVETICA, 16, Font.BOLD, Color.WHITE));
        p.setAlignment(Element.ALIGN_CENTER);
        cell.addElement(p);

        Paragraph l = new Paragraph(label,
                new Font(Font.HELVETICA, 9, Font.NORMAL, Color.WHITE));
        l.setAlignment(Element.ALIGN_CENTER);
        cell.addElement(l);

        table.addCell(cell);
    }

    private static void addTableHeader(PdfPTable table, String... headers) {
        for (String h : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(h, FONT_HEADER));
            cell.setBackgroundColor(COLOR_PRIMARY);
            cell.setPadding(5);
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            table.addCell(cell);
        }
    }

    private static void addTableRow(PdfPTable table, String... values) {
        for (String v : values) {
            PdfPCell cell = new PdfPCell(new Phrase(v, FONT_NORMAL));
            cell.setPadding(4);
            table.addCell(cell);
        }
    }

    private static void addTableRowColored(PdfPTable table, Color bg,
                                            String... values) {
        for (String v : values) {
            PdfPCell cell = new PdfPCell(new Phrase(v, FONT_NORMAL));
            cell.setBackgroundColor(bg);
            cell.setPadding(4);
            table.addCell(cell);
        }
    }

    private static void addTableRowColored(PdfPTable table, String v1,
                                            String v2, String v3, String v4,
                                            String v5, Color bg) {
        addTableRowColored(table, bg, v1, v2, v3, v4, v5);
    }

    private static String getDE039Label(String code) {
        return switch (code) {
            case "00" -> "Approuvée";
            case "05" -> "Refusée";
            case "51" -> "Provision insuffisante";
            case "14" -> "Numéro de carte invalide";
            case "54" -> "Carte expirée";
            case "55" -> "PIN incorrect";
            case "57" -> "Transaction non autorisée";
            case "61" -> "Plafond dépassé";
            default   -> "Autre";
        };
    }
}
