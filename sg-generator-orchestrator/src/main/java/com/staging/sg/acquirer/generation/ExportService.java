package com.staging.sg.acquirer.generation;

import com.staging.sg.common.entity.GeneratedTransaction;
import com.staging.sg.common.repository.GeneratedTransactionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Export des transactions générées d'une campagne en JSON ou CSV.
 * Structure FIXE : tous les champs DE (les non générés apparaissent vides).
 * Écrit le fichier sur disque dans {reports.base-dir}/exports et renvoie le chemin.
 */
@Service
public class ExportService {

    private static final Logger log = LoggerFactory.getLogger(ExportService.class);

    private final GeneratedTransactionRepository txRepo;
    private final ObjectMapper objectMapper;

    @Value("${reports.base-dir:reports}")
    private String reportsBaseDir;

    public ExportService(GeneratedTransactionRepository txRepo, ObjectMapper objectMapper) {
        this.txRepo = txRepo;
        this.objectMapper = objectMapper;
    }

    private static final String[] HEADERS = {
        "id","campaignId","txType","DE2_pan","DE3_processingCode","DE4_amount",
        "DE7_transmissionDt","DE11_stan","DE12_localTime","DE13_localDate","DE14_expiry",
        "DE18_mcc","DE22_posEntryMode","DE25_posCondition","DE32_acquirerId","DE37_rrn",
        "DE41_terminalId","DE42_merchantId","DE43_merchantNameLoc","DE49_currency"
    };

    /** Génère le fichier sur disque, renvoie le chemin absolu créé. */
    public String export(Long campaignId, String format) throws IOException {
        List<GeneratedTransaction> txs = txRepo.findByCampaignId(campaignId);
        if (txs.isEmpty()) {
            throw new IllegalStateException("Aucune transaction pour la campagne " + campaignId + " (générer d'abord)");
        }

        boolean csv = "csv".equalsIgnoreCase(format);
        String content = csv ? toCsv(txs) : toJson(txs);
        String ext = csv ? "csv" : "json";

        Path dir = Paths.get(reportsBaseDir, "exports");
        Files.createDirectories(dir);

        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String fileName = "campaign_" + campaignId + "_" + ts + "." + ext;
        Path target = dir.resolve(fileName);
        Files.writeString(target, content);

        String abs = target.toAbsolutePath().toString();
        log.info("[EXPORT] Campagne {} -> {} ({} transactions)", campaignId, abs, txs.size());
        return abs;
    }

    private String toJson(List<GeneratedTransaction> txs) throws IOException {
        return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(txs);
    }

    private String toCsv(List<GeneratedTransaction> txs) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.join(",", HEADERS)).append("\n");
        for (GeneratedTransaction t : txs) {
            sb.append(csv(t.getId()))
              .append(",").append(csv(t.getCampaignId()))
              .append(",").append(csv(t.getTxType()))
              .append(",").append(csv(t.getDe2Pan()))
              .append(",").append(csv(t.getDe3ProcessingCode()))
              .append(",").append(csv(t.getDe4Amount()))
              .append(",").append(csv(t.getDe7TransmissionDt()))
              .append(",").append(csv(t.getDe11Stan()))
              .append(",").append(csv(t.getDe12LocalTime()))
              .append(",").append(csv(t.getDe13LocalDate()))
              .append(",").append(csv(t.getDe14Expiry()))
              .append(",").append(csv(t.getDe18Mcc()))
              .append(",").append(csv(t.getDe22PosEntryMode()))
              .append(",").append(csv(t.getDe25PosCondition()))
              .append(",").append(csv(t.getDe32AcquirerId()))
              .append(",").append(csv(t.getDe37Rrn()))
              .append(",").append(csv(t.getDe41TerminalId()))
              .append(",").append(csv(t.getDe42MerchantId()))
              .append(",").append(csv(t.getDe43MerchantNameLoc()))
              .append(",").append(csv(t.getDe49Currency()))
              .append("\n");
        }
        return sb.toString();
    }

    private String csv(Object v) {
        if (v == null) return "";
        String s = String.valueOf(v);
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            s = "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }
}
