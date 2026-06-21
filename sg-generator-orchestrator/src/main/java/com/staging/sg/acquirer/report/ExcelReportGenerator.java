package com.staging.sg.acquirer.report;

import com.staging.sg.common.entity.Execution;
import com.staging.sg.common.entity.Result;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileOutputStream;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ExcelReportGenerator {

    private static final Logger log = LoggerFactory.getLogger(ExcelReportGenerator.class);

    public static void generate(Path outputPath, Execution exec, String testName, List<Result> results) {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {

            // ── Styles ───────────────────────────────────────
            CellStyle titleStyle = createTitleStyle(wb);
            CellStyle headerStyle = createHeaderStyle(wb);
            CellStyle kpiStyle = createKpiStyle(wb);
            CellStyle normalStyle = createNormalStyle(wb);
            CellStyle altStyle = createAltStyle(wb);

            // ── Sheet 1 — Résumé ─────────────────────────────
            XSSFSheet sheet1 = wb.createSheet("Résumé");
            sheet1.setColumnWidth(0, 6000);
            sheet1.setColumnWidth(1, 8000);

            int row = 0;

            // Title
            Row titleRow = sheet1.createRow(row++);
            Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue("ScenarioGenerator — Rapport TPS");
            titleCell.setCellStyle(titleStyle);
            sheet1.addMergedRegion(new CellRangeAddress(0, 0, 0, 3));

            row++; // empty row

            // Info
            addSheetRow(sheet1, row++, headerStyle, "Information", "Valeur");

            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
            addSheetRow(sheet1, row++, normalStyle,
                    "Test", testName);
            addSheetRow(sheet1, row++, normalStyle,
                    "Execution ID", String.valueOf(exec.getId()));
            addSheetRow(sheet1, row++, normalStyle,
                    "Mode", exec.getMode() != null ? exec.getMode().name() : "N/A");
            addSheetRow(sheet1, row++, normalStyle,
                    "Status", exec.getStatus() != null ? exec.getStatus().name() : "N/A");
            addSheetRow(sheet1, row++, normalStyle,
                    "Démarré le", exec.getStartedAt() != null ? exec.getStartedAt().format(fmt) : "N/A");
            addSheetRow(sheet1, row++, normalStyle,
                    "Terminé le", exec.getEndedAt() != null ? exec.getEndedAt().format(fmt) : "N/A");

            row++;

            // KPIs
            addSheetRow(sheet1, row++, headerStyle, "KPI", "Valeur");

            long approved = results.stream().filter(r -> Boolean.TRUE.equals(r.getApproved())).count();
            long declined = results.stream().filter(r -> Boolean.FALSE.equals(r.getApproved())).count();
            double rate = results.isEmpty() ? 0 : (double) approved / results.size() * 100;

            addSheetRow(sheet1, row++, kpiStyle, "TX Total",    String.valueOf(results.size()));
            addSheetRow(sheet1, row++, kpiStyle, "Approuvées",  approved + " (" + String.format("%.1f%%", rate) + ")");
            addSheetRow(sheet1, row++, kpiStyle, "Refusées",    String.valueOf(declined));

            row++;

            // Metrics
            addSheetRow(sheet1, row++, headerStyle, "Métrique", "Valeur");
            if (exec.getTpsActualAvg()    != null) addSheetRow(sheet1, row++, normalStyle, "TPS Moyen",    exec.getTpsActualAvg() + " /s");
            if (exec.getResponseTimeAvg() != null) addSheetRow(sheet1, row++, normalStyle, "Réponse Avg",  exec.getResponseTimeAvg() + " ms");
            if (exec.getResponseTimeMin() != null) addSheetRow(sheet1, row++, normalStyle, "Réponse Min",  exec.getResponseTimeMin() + " ms");
            if (exec.getResponseTimeMax() != null) addSheetRow(sheet1, row++, normalStyle, "Réponse Max",  exec.getResponseTimeMax() + " ms");
            if (exec.getResponseTimeP95() != null) addSheetRow(sheet1, row++, normalStyle, "P95",          exec.getResponseTimeP95() + " ms");
            if (exec.getResponseTimeP99() != null) addSheetRow(sheet1, row++, normalStyle, "P99",          exec.getResponseTimeP99() + " ms");

            // ── Sheet 2 — Transactions ───────────────────────
            XSSFSheet sheet2 = wb.createSheet("Transactions");
            sheet2.setColumnWidth(0, 2000);
            sheet2.setColumnWidth(1, 6000);
            sheet2.setColumnWidth(2, 3000);
            sheet2.setColumnWidth(3, 4000);
            sheet2.setColumnWidth(4, 3000);

            int row2 = 0;
            addSheetRow5(sheet2, row2++, headerStyle,
                    "#", "PAN", "DE039", "Auth Code", "Durée (ms)");

            int count = 0;
            for (Result r : results) {
                CellStyle style = count % 2 == 0 ? normalStyle : altStyle;
                addSheetRow5(sheet2, row2++, style,
                        String.valueOf(++count),
                        r.getPanMasked() != null ? r.getPanMasked() : "N/A",
                        r.getDe039() != null ? r.getDe039() : "N/A",
                        r.getDe038AuthCode() != null ? r.getDe038AuthCode() : "N/A",
                        r.getDurationMs() != null ? String.valueOf(r.getDurationMs()) : "N/A");
            }

            // ── Sheet 3 — DE039 distribution ─────────────────
            XSSFSheet sheet3 = wb.createSheet("Codes réponse");
            sheet3.setColumnWidth(0, 4000);
            sheet3.setColumnWidth(1, 6000);
            sheet3.setColumnWidth(2, 4000);

            int row3 = 0;
            addSheetRow(sheet3, row3++, headerStyle, "Code DE039", "Nombre");

            Map<String, Long> de039 = results.stream()
                    .collect(Collectors.groupingBy(
                            r -> r.getDe039() != null ? r.getDe039() : "N/A",
                            Collectors.counting()));

            de039.forEach((code, cnt) ->
                addSheetRow(sheet3, sheet3.getLastRowNum() + 1, normalStyle,
                        code, String.valueOf(cnt)));

            // Save
            try (FileOutputStream fos = new FileOutputStream(outputPath.toFile())) {
                wb.write(fos);
            }
            log.info("[EXCEL] Generated : {}", outputPath);

        } catch (Exception e) {
            log.error("[EXCEL] Error : {}", e.getMessage(), e);
        }
    }

    // ── Style helpers ─────────────────────────────────────────

    private static CellStyle createTitleStyle(XSSFWorkbook wb) {
        CellStyle s = wb.createCellStyle();
        XSSFFont f = wb.createFont();
        f.setBold(true); f.setFontHeightInPoints((short) 14);
        f.setColor(new XSSFColor(new byte[]{(byte)255,(byte)255,(byte)255}, null));
        s.setFont(f);
        s.setFillForegroundColor(new XSSFColor(new byte[]{(byte)33,(byte)97,(byte)140}, null));
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        s.setAlignment(HorizontalAlignment.CENTER);
        return s;
    }

    private static CellStyle createHeaderStyle(XSSFWorkbook wb) {
        CellStyle s = wb.createCellStyle();
        XSSFFont f = wb.createFont();
        f.setBold(true);
        f.setColor(new XSSFColor(new byte[]{(byte)255,(byte)255,(byte)255}, null));
        s.setFont(f);
        s.setFillForegroundColor(new XSSFColor(new byte[]{(byte)33,(byte)97,(byte)140}, null));
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return s;
    }

    private static CellStyle createKpiStyle(XSSFWorkbook wb) {
        CellStyle s = wb.createCellStyle();
        XSSFFont f = wb.createFont(); f.setBold(true);
        s.setFont(f);
        s.setFillForegroundColor(new XSSFColor(new byte[]{(byte)214,(byte)234,(byte)248}, null));
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return s;
    }

    private static CellStyle createNormalStyle(XSSFWorkbook wb) {
        return wb.createCellStyle();
    }

    private static CellStyle createAltStyle(XSSFWorkbook wb) {
        CellStyle s = wb.createCellStyle();
        s.setFillForegroundColor(new XSSFColor(new byte[]{(byte)236,(byte)240,(byte)241}, null));
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return s;
    }

    private static void addSheetRow(XSSFSheet sheet, int rowNum,
                                     CellStyle style, String... values) {
        Row row = sheet.createRow(rowNum);
        for (int i = 0; i < values.length; i++) {
            Cell cell = row.createCell(i);
            cell.setCellValue(values[i]);
            cell.setCellStyle(style);
        }
    }

    private static void addSheetRow5(XSSFSheet sheet, int rowNum,
                                      CellStyle style, String... values) {
        addSheetRow(sheet, rowNum, style, values);
    }
}
