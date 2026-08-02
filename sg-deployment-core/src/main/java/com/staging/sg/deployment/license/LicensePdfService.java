package com.staging.sg.deployment.license;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfWriter;

import java.awt.Color;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

public final class LicensePdfService {
    private static final Color NAVY = new Color(24, 48, 82);
    private static final Color BLUE = new Color(44, 112, 180);
    private static final Color LIGHT = new Color(238, 244, 250);

    public void generate(TechnicalLicense license, Path technicalLicense, Path output) throws Exception {
        Files.createDirectories(output.toAbsolutePath().normalize().getParent());
        String technicalHash = sha256(technicalLicense);
        try (OutputStream stream = Files.newOutputStream(output)) {
            Document document = new Document(PageSize.A4, 48, 48, 54, 48);
            PdfWriter writer = PdfWriter.getInstance(document, stream);
            writer.setPageEvent(new PageDecoration(license.localTest()));
            document.open();

            Font title = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 22, NAVY);
            Font subtitle = FontFactory.getFont(FontFactory.HELVETICA, 10, Color.DARK_GRAY);
            Font section = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13, NAVY);
            Font normal = FontFactory.getFont(FontFactory.HELVETICA, 10, Color.BLACK);

            Paragraph heading = new Paragraph("SCENARIOGENERATOR - LICENCE LOGICIELLE", title);
            heading.setAlignment(Element.ALIGN_CENTER);
            document.add(heading);
            Paragraph reference = new Paragraph("Licence " + license.licenseId(), subtitle);
            reference.setAlignment(Element.ALIGN_CENTER);
            reference.setSpacingAfter(22);
            document.add(reference);

            document.add(new Paragraph("Client membre licencié", section));
            document.add(detailsTable(List.of(
                    row("Code client", license.clientCode()),
                    row("Raison sociale / nom commercial", license.clientName()),
                    row("Environnement", license.environmentCode()),
                    row("Version des bundles", license.bundleVersion()),
                    row("Valide à partir du", license.validFrom()),
                    row("Valide jusqu'au", license.validUntil()),
                    row("Approuvée par", license.approvedBy())
            ), normal));

            document.add(spacedSection("Modules côté membre autorisés", section));
            document.add(moduleTable(license.memberModules(), normal));
            document.add(spacedSection("Simulateurs autorisés", section));
            document.add(moduleTable(license.simulatorModules(), normal));

            document.add(spacedSection("Vérification technique", section));
            Paragraph hash = new Paragraph("SHA-256 de license.json.sig :\n" + technicalHash, normal);
            hash.setLeading(16);
            document.add(hash);

            Paragraph notice = new Paragraph(
                    "Ce document ne contient aucun mot de passe, clé cryptographique, PAN ou secret d'environnement. "
                            + "L'autorisation d'exécution est contrôlée par la licence technique signée séparément.",
                    subtitle);
            notice.setSpacingBefore(24);
            notice.setLeading(15);
            document.add(notice);
            document.close();
        }
    }

    private static PdfPTable detailsTable(List<String[]> rows, Font font) {
        PdfPTable table = new PdfPTable(new float[]{1.3f, 2.7f});
        table.setWidthPercentage(100);
        table.setSpacingBefore(8);
        for (String[] row : rows) {
            PdfPCell label = cell(row[0], font, LIGHT);
            label.setPadding(7);
            PdfPCell value = cell(row[1], font, Color.WHITE);
            value.setPadding(7);
            table.addCell(label);
            table.addCell(value);
        }
        return table;
    }

    private static PdfPTable moduleTable(List<String> modules, Font font) {
        PdfPTable table = new PdfPTable(1);
        table.setWidthPercentage(100);
        if (modules.isEmpty()) {
            table.addCell(cell("Aucun", font, Color.WHITE));
        } else {
            modules.forEach(module -> table.addCell(cell("- " + module, font, Color.WHITE)));
        }
        return table;
    }

    private static PdfPCell cell(String text, Font font, Color background) {
        PdfPCell cell = new PdfPCell(new Phrase(text == null ? "" : text, font));
        cell.setBorderColor(new Color(205, 215, 225));
        cell.setBackgroundColor(background);
        cell.setPadding(6);
        return cell;
    }

    private static Paragraph spacedSection(String text, Font font) {
        Paragraph paragraph = new Paragraph(text, font);
        paragraph.setSpacingBefore(18);
        paragraph.setSpacingAfter(6);
        return paragraph;
    }

    private static String[] row(String label, String value) {
        return new String[]{label, value};
    }

    private static String sha256(Path path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(digest.digest(Files.readAllBytes(path))).toUpperCase();
    }

    private static final class PageDecoration extends PdfPageEventHelper {
        private final boolean localTest;

        private PageDecoration(boolean localTest) { this.localTest = localTest; }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            Rectangle page = document.getPageSize();
            ColumnText.showTextAligned(writer.getDirectContent(), Element.ALIGN_CENTER,
                    new Phrase("ScenarioGenerator - Licence", FontFactory.getFont(FontFactory.HELVETICA, 8, Color.GRAY)),
                    page.getWidth() / 2, 24, 0);
            if (localTest) {
                ColumnText.showTextAligned(writer.getDirectContentUnder(), Element.ALIGN_CENTER,
                        new Phrase("TEST LOCAL - NON CONTRACTUEL",
                                FontFactory.getFont(FontFactory.HELVETICA_BOLD, 34, new Color(225, 225, 225))),
                        page.getWidth() / 2, page.getHeight() / 2, 45);
            }
        }
    }
}
