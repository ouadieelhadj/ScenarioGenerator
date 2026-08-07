package com.staging.sg.switchlab.bff.service;

import com.lowagie.text.Document;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.staging.sg.switchlab.contracts.SwitchLabCampaignReport;
import com.staging.sg.switchlab.contracts.SwitchLabCampaignTestResult;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

@Service
public class SwitchLabReportExportService {
    public byte[] pdf(SwitchLabCampaignReport report) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Document document = new Document();
        PdfWriter.getInstance(document, output);
        document.open();
        document.add(new Paragraph("FuturPayment SwitchLab - Campaign report"));
        document.add(new Paragraph("Execution: " + report.executionId()));
        document.add(new Paragraph("Verdict: " + report.verdict()));
        document.add(new Paragraph("Availability: " + report.actualAvailabilityPercent() + "%"));
        document.add(new Paragraph("Samples: " + report.sampleCount() + " | Error rate: "
                + report.errorRatePercent() + "% | p95: " + report.p95ResponseTimeMs() + " ms"));
        PdfPTable table = new PdfPTable(5);
        for (String header : new String[]{"Test", "Module", "Samples", "p95", "Verdict"}) table.addCell(header);
        for (SwitchLabCampaignTestResult result : report.results()) {
            table.addCell(result.testCode()); table.addCell(result.moduleCode());
            table.addCell(String.valueOf(result.sampleCount())); table.addCell(result.p95ResponseTimeMs() + " ms");
            table.addCell(result.verdict());
        }
        document.add(table);
        document.close();
        return output.toByteArray();
    }

    public byte[] xlsx(SwitchLabCampaignReport report) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet summary = workbook.createSheet("Summary");
            add(summary, 0, "Execution", report.executionId()); add(summary, 1, "Verdict", report.verdict());
            add(summary, 2, "Availability (%)", report.actualAvailabilityPercent());
            add(summary, 3, "Samples", report.sampleCount()); add(summary, 4, "Error rate (%)", report.errorRatePercent());
            add(summary, 5, "p95 (ms)", report.p95ResponseTimeMs());
            Sheet details = workbook.createSheet("Results");
            Row header = details.createRow(0);
            String[] columns = {"Test", "Module", "Samples", "Successes", "Errors", "p95 (ms)", "Verdict"};
            for (int i = 0; i < columns.length; i++) header.createCell(i).setCellValue(columns[i]);
            int rowIndex = 1;
            for (SwitchLabCampaignTestResult result : report.results()) {
                Row row = details.createRow(rowIndex++);
                row.createCell(0).setCellValue(result.testCode()); row.createCell(1).setCellValue(result.moduleCode());
                row.createCell(2).setCellValue(result.sampleCount()); row.createCell(3).setCellValue(result.successCount());
                row.createCell(4).setCellValue(result.errorCount()); row.createCell(5).setCellValue(result.p95ResponseTimeMs());
                row.createCell(6).setCellValue(result.verdict());
            }
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot export XLSX report", failure);
        }
    }

    private void add(Sheet sheet, int index, String label, Object value) {
        Row row = sheet.createRow(index); row.createCell(0).setCellValue(label); row.createCell(1).setCellValue(String.valueOf(value));
    }
}
