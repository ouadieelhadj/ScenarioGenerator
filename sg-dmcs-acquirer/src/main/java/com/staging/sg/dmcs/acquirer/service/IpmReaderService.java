package com.staging.sg.dmcs.acquirer.service;

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

@Service
public class IpmReaderService {

    private static final Logger log = LoggerFactory.getLogger(IpmReaderService.class);

    private final AcqIpmFileRepository       acqIpmFileRepository;
    private final AcqIpmRecordRepository     acqIpmRecordRepository;
    private final IpmProcessingLogRepository logRepository;

    public IpmReaderService(AcqIpmFileRepository acqIpmFileRepository,
                            AcqIpmRecordRepository acqIpmRecordRepository,
                            IpmProcessingLogRepository logRepository) {
        this.acqIpmFileRepository   = acqIpmFileRepository;
        this.acqIpmRecordRepository = acqIpmRecordRepository;
        this.logRepository          = logRepository;
    }

    @Transactional
    public AcqIpmFile readFile(String filePath) throws Exception {
        Path path = Paths.get(filePath);
        if (!Files.exists(path)) {
            throw new IllegalArgumentException("File not found: " + filePath);
        }
        String content  = new String(Files.readAllBytes(path));
        String checksum = sha256(content);
        String fileName = path.getFileName().toString();

        Optional<IpmProcessingLog> existing =
                logRepository.findByChecksumAndRoleAndDirection(checksum, "ACQUIRER", "IN");
        if (existing.isPresent()) {
            log.warn("[DMCS-ACQ-READ] File already read: {}", fileName);
            throw new IllegalStateException("File already processed: " + fileName);
        }

        AcqIpmFile ipmFile = new AcqIpmFile();
        ipmFile.setFileName(fileName);
        ipmFile.setFileDate(LocalDate.now());
        ipmFile.setDirection("IN");
        ipmFile.setStatus("READ");
        ipmFile.setProcessingMode("TEST");
        ipmFile.setFilePathAscii(filePath);
        ipmFile = acqIpmFileRepository.save(ipmFile);

        List<AcqIpmRecord> records = new ArrayList<>();
        int msgNum = 0;
        long totalAmount = 0L;
        int cbCount = 0;

        for (String line : content.split("\n")) {
            line = line.trim();
            if (!line.matches("^\\d{4}\\|.*")) continue;

            String[] parts = line.split("\\|");
            String mti  = parts[0];
            String func = parts.length > 1 ? parts[1] : "";

            AcqIpmRecord r = new AcqIpmRecord();
            r.setIpmFile(ipmFile);
            r.setDirection("IN");
            r.setMessageNumber(++msgNum);
            r.setMti(mti);
            r.setFunctionCode(func);
            r.setRawAscii(line);

            if (mti.equals("1442")) {
                r.setRecordType("CHARGEBACK");
                cbCount++;
            } else if (mti.equals("1644") && func.equals("696")) {
                r.setRecordType("ADDENDUM");
            } else if (mti.equals("1644")) {
                r.setRecordType("HEADER_TRAILER");
            } else {
                r.setRecordType("OTHER");
            }

            for (String p : parts) {
                int eq = p.indexOf('=');
                if (eq <= 0) continue;
                String key = p.substring(0, eq);
                String val = p.substring(eq + 1);
                switch (key) {
                    case "PAN":    r.setDe002Pan(val); break;
                    case "PC":     r.setDe003ProcCode(val); break;
                    case "AMT":    try { r.setDe004Amount(Long.parseLong(val)); } catch (Exception ignored) {} break;
                    case "RRN":    r.setDe037Rrn(val); break;
                    case "AUTH":   r.setDe038AuthCode(val); break;
                    case "MCC":    r.setDe026Mcc(val); break;
                    case "TID":    r.setDe041TermId(val); break;
                    case "MID":    r.setDe042MerchId(val); break;
                    case "CCY":    r.setDe049Currency(val); break;
                    case "REASON": r.setDe025Reason(val); break;
                    case "PDS":    r.setPdsData(val); break;
                    case "MSG":    r.setDe071MsgNum(val); break;
                    default: break;
                }
            }
            if (r.getDe004Amount() != null && mti.equals("1442")) {
                totalAmount += r.getDe004Amount();
            }
            records.add(r);
        }

        acqIpmRecordRepository.saveAll(records);

        ipmFile.setNbTransactions(cbCount);
        ipmFile.setTotalAmount(totalAmount);
        ipmFile = acqIpmFileRepository.save(ipmFile);

        IpmProcessingLog plog = new IpmProcessingLog();
        plog.setFileName(fileName);
        plog.setFilePath(filePath);
        plog.setRole("ACQUIRER");
        plog.setDirection("IN");
        plog.setAction("READ");
        plog.setRecordCount(records.size());
        plog.setChecksum(checksum);
        plog.setStatus("DONE");
        logRepository.save(plog);

        log.info("[DMCS-ACQ-READ] Read {} ({} records, {} chargebacks)",
                fileName, records.size(), cbCount);
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
