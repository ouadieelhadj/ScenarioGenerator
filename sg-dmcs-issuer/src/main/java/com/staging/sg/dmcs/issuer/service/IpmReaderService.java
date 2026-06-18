package com.staging.sg.dmcs.issuer.service;

import com.staging.sg.common.entity.*;
import com.staging.sg.common.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Lecture (côté ÉMETTEUR) des fichiers IPM reçus de l'acquéreur :
 * 1240 First Presentment + 1644 Financial Detail Addendum.
 * Enregistre dans iss_ipm_* avec direction=IN.
 * Anti-doublon par checksum via ipm_processing_log.
 */
@Service
public class IpmReaderService {

    private static final Logger log = LoggerFactory.getLogger(IpmReaderService.class);

    private final IssIpmFileRepository       issIpmFileRepository;
    private final IssIpmRecordRepository     issIpmRecordRepository;
    private final IpmProcessingLogRepository logRepository;

    public IpmReaderService(IssIpmFileRepository issIpmFileRepository,
                            IssIpmRecordRepository issIpmRecordRepository,
                            IpmProcessingLogRepository logRepository) {
        this.issIpmFileRepository   = issIpmFileRepository;
        this.issIpmRecordRepository = issIpmRecordRepository;
        this.logRepository          = logRepository;
    }

    @Transactional
    public IssIpmFile readFile(String filePath) throws Exception {
        Path path = Paths.get(filePath);
        if (!Files.exists(path)) {
            throw new IllegalArgumentException("File not found: " + filePath);
        }
        String content  = new String(Files.readAllBytes(path));
        String checksum = sha256(content);
        String fileName = path.getFileName().toString();

        // Anti-doublon : déjà lu (même checksum, role ISSUER, direction IN) ?
        Optional<IpmProcessingLog> existing =
                logRepository.findByChecksumAndRoleAndDirection(checksum, "ISSUER", "IN");
        if (existing.isPresent()) {
            log.warn("[DMCS-ISS-READ] File already read (checksum match): {}", fileName);
            throw new IllegalStateException("File already processed: " + fileName);
        }

        // Créer le fichier reçu (direction IN)
        IssIpmFile ipmFile = new IssIpmFile();
        ipmFile.setFileName(fileName);
        ipmFile.setFileDate(LocalDate.now());
        ipmFile.setDirection("IN");
        ipmFile.setStatus("READ");
        ipmFile.setProcessingMode("TEST");
        ipmFile.setFilePathAscii(filePath);
        ipmFile = issIpmFileRepository.save(ipmFile);

        List<IssIpmRecord> records = new ArrayList<>();
        int msgNum = 0;
        long totalAmount = 0L;
        int txCount = 0;

        for (String line : content.split("\n")) {
            line = line.trim();
            // On ne parse que les lignes de message (commencent par un MTI 4 chiffres + |)
            if (!line.matches("^\\d{4}\\|.*")) continue;

            String[] parts = line.split("\\|");
            String mti  = parts[0];
            String func = parts.length > 1 ? parts[1] : "";

            IssIpmRecord r = new IssIpmRecord();
            r.setIpmFile(ipmFile);
            r.setDirection("IN");
            r.setMessageNumber(++msgNum);
            r.setMti(mti);
            r.setFunctionCode(func);
            r.setRawAscii(line);

            // Type de record selon MTI/function
            if (mti.equals("1240")) {
                r.setRecordType("PRESENTMENT");
                txCount++;
            } else if (mti.equals("1644") && func.equals("696")) {
                r.setRecordType("ADDENDUM");
            } else if (mti.equals("1644")) {
                r.setRecordType(func.equals("685") ? "HEADER" : "TRAILER");
            } else {
                r.setRecordType("OTHER");
            }

            // Extraire les champs KEY=VALUE
            for (String p : parts) {
                int eq = p.indexOf('=');
                if (eq <= 0) continue;
                String key = p.substring(0, eq);
                String val = p.substring(eq + 1);
                switch (key) {
                    case "PAN":      r.setDe002Pan(val); break;
                    case "PC":       r.setDe003ProcCode(val); break;
                    case "AMT":      try { r.setDe004Amount(Long.parseLong(val)); totalAmount += r.getDe004Amount() != null ? 0 : 0; } catch (Exception ignored) {} break;
                    case "RRN":      r.setDe037Rrn(val); break;
                    case "AUTH":     r.setDe038AuthCode(val); break;
                    case "MCC":      r.setDe026Mcc(val); break;
                    case "TID":      r.setDe041TermId(val); break;
                    case "MID":      r.setDe042MerchId(val); break;
                    case "CCY":      r.setDe049Currency(val); break;
                    case "ARN":      r.setDe031AcqRefData(val); break;
                    case "PDS":      r.setPdsData(val); break;
                    case "MSG":      r.setDe071MsgNum(val); break;
                    default: break;
                }
            }
            if (r.getDe004Amount() != null && mti.equals("1240")) {
                totalAmount += r.getDe004Amount();
            }
            records.add(r);
        }

        issIpmRecordRepository.saveAll(records);

        ipmFile.setNbTransactions(txCount);
        ipmFile.setTotalAmount(totalAmount);
        ipmFile = issIpmFileRepository.save(ipmFile);

        // Journal
        IpmProcessingLog plog = new IpmProcessingLog();
        plog.setFileName(fileName);
        plog.setFilePath(filePath);
        plog.setRole("ISSUER");
        plog.setDirection("IN");
        plog.setAction("READ");
        plog.setRecordCount(records.size());
        plog.setChecksum(checksum);
        plog.setStatus("DONE");
        logRepository.save(plog);

        log.info("[DMCS-ISS-READ] Read {} ({} records, {} presentments)",
                fileName, records.size(), txCount);
        return ipmFile;
    }

    private String sha256(String data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] h = md.digest(data.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : h) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }
}
