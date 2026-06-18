package com.staging.sg.dmcs.issuer.service;

import com.staging.sg.common.entity.*;
import com.staging.sg.common.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
public class IpmChargebackService {

    private static final Logger log = LoggerFactory.getLogger(IpmChargebackService.class);

    private final IssAuthorizationRepository issAuthRepository;
    private final IssIpmFileRepository       issIpmFileRepository;
    private final IssIpmRecordRepository     issIpmRecordRepository;
    private final IpmProcessingLogRepository logRepository;

    @Value("${dmcs.base-dir:D:/MoneyCore/ScenarioGenerator/dmcs}")
    private String baseDir;

    @Value("${dmcs.file-id-prefix:MC}")
    private String fileIdPrefix;

    @Value("${mc.issuer.defaults.DE093_DEST_ID:411111}")
    private String destId;

    @Value("${mc.issuer.defaults.DE094_ORIGIN_ID:555555}")
    private String originId;

    public IpmChargebackService(IssAuthorizationRepository issAuthRepository,
                                IssIpmFileRepository issIpmFileRepository,
                                IssIpmRecordRepository issIpmRecordRepository,
                                IpmProcessingLogRepository logRepository) {
        this.issAuthRepository      = issAuthRepository;
        this.issIpmFileRepository   = issIpmFileRepository;
        this.issIpmRecordRepository = issIpmRecordRepository;
        this.logRepository          = logRepository;
    }

    // ── Génère un fichier 1442 First Chargeback (côté émetteur) ──
    @Transactional
    public IssIpmFile generateChargeback(Long executionId, String functionCode, String reasonCode, int limit) {
        // Charger les autorisations approuvées non encore chargées
        List<IssAuthorization> auths = issAuthRepository.findByApprovedTrue();
        List<IssAuthorization> eligible = new ArrayList<>();
        for (IssAuthorization a : auths) {
            if (Boolean.TRUE.equals(a.getApproved())
                    && !Boolean.TRUE.equals(a.getIpmGenerated())) {
                eligible.add(a);
                if (limit > 0 && eligible.size() >= limit) break;
            }
        }
        log.info("[DMCS-ISS] {} eligible authorizations for chargeback (function={}, reason={})",
                eligible.size(), functionCode, reasonCode);

        // Créer le fichier
        String stamp    = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String fileName = stamp;
        LocalDate today = LocalDate.now();

        IssIpmFile ipmFile = new IssIpmFile();
        ipmFile.setFileName(fileName);
        ipmFile.setFileDate(today);
        ipmFile.setDirection("OUT");
        ipmFile.setStatus("GENERATED");
        ipmFile.setProcessingMode("TEST");
        ipmFile.setExecutionId(executionId);
        ipmFile.setFileId(fileIdPrefix + stamp.replace("_", ""));
        ipmFile = issIpmFileRepository.save(ipmFile);

        List<IssIpmRecord> records = new ArrayList<>();
        StringBuilder ascii = new StringBuilder();
        long totalAmount = 0L;
        int msgNum = 1;

        // Header
        IssIpmRecord header = new IssIpmRecord();
        header.setIpmFile(ipmFile);
        header.setDirection("OUT");
        header.setMessageNumber(msgNum++);
        header.setRecordType("HEADER");
        header.setMti("1644");
        header.setFunctionCode("697");
        header.setRawAscii("HEADER|1644|697|FILE=" + ipmFile.getFileId());
        records.add(header);
        ascii.append(header.getRawAscii()).append("\n");

        // 1442 Chargeback par transaction
        for (IssAuthorization a : eligible) {
            IssIpmRecord r = new IssIpmRecord();
            r.setIpmFile(ipmFile);
            r.setIssAuthId(a.getId());
            r.setDirection("OUT");
            r.setMessageNumber(msgNum);
            r.setRecordType("CHARGEBACK");
            r.setMti("1442");
            r.setFunctionCode(functionCode);
            r.setDe002Pan(a.getDe002Pan());
            r.setDe003ProcCode(a.getDe003ProcCode());
            r.setDe004Amount(a.getDe004Amount());
            r.setDe005AmountRecon(a.getDe004Amount());
            r.setDe012LocalDt(a.getDe012LocalTime());
            r.setDe022PosCode(a.getDe022PosMode());
            r.setDe024FuncCode(functionCode);
            r.setDe025Reason(reasonCode);
            r.setDe026Mcc(a.getDe018Mcc());
            r.setDe030OrigAmount(a.getDe004Amount());
            r.setDe032AcqId(a.getDe032AcqId());
            r.setDe037Rrn(a.getDe037Rrn());
            r.setDe038AuthCode(a.getDe038AuthCode());
            r.setDe041TermId(a.getDe041TermId());
            r.setDe042MerchId(a.getDe042MerchId());
            r.setDe043MerchName(a.getDe043MerchName());
            r.setDe049Currency(a.getDe049Currency());
            r.setDe050CurrencyRecon(a.getDe049Currency());
            r.setDe071MsgNum(String.format("%08d", msgNum));
            r.setDe093DestId(destId);
            r.setDe094OriginId(originId);

            String line = String.format(
                "1442|%s|TYPE=CHARGEBACK|PAN=%s|PC=%s|AMT=%012d|RRN=%s|" +
                "AUTH=%s|MCC=%s|TID=%s|MID=%s|CCY=%s|REASON=%s|ORIG=%012d|" +
                "DEST=%s|ORIGIN=%s|MSG=%08d",
                functionCode,
                safe(a.getDe002Pan()), safe(a.getDe003ProcCode()),
                a.getDe004Amount() != null ? a.getDe004Amount() : 0,
                safe(a.getDe037Rrn()), safe(a.getDe038AuthCode()),
                safe(a.getDe018Mcc()), safe(a.getDe041TermId()),
                safe(a.getDe042MerchId()), safe(a.getDe049Currency()),
                safe(reasonCode),
                a.getDe004Amount() != null ? a.getDe004Amount() : 0,
                destId, originId, msgNum);
            r.setRawAscii(line);
            r.setRawHex(toHex(line.getBytes()));
            records.add(r);
            ascii.append(line).append("\n");

            if (a.getDe004Amount() != null) totalAmount += a.getDe004Amount();

            // Poser les flags anti-doublon
            a.setIpmGenerated(true);
            a.setIpmFileId(ipmFile.getId());
            a.setIpmFileName(fileName);
            a.setIpmGeneratedAt(LocalDateTime.now());
            issAuthRepository.save(a);

            msgNum++;
        }

        // Trailer
        IssIpmRecord trailer = new IssIpmRecord();
        trailer.setIpmFile(ipmFile);
        trailer.setDirection("OUT");
        trailer.setMessageNumber(msgNum);
        trailer.setRecordType("TRAILER");
        trailer.setMti("1644");
        trailer.setFunctionCode("695");
        trailer.setRawAscii(String.format("TRAILER|1644|695|COUNT=%d|TOTAL=%012d",
                eligible.size(), totalAmount));
        records.add(trailer);
        ascii.append(trailer.getRawAscii()).append("\n");

        issIpmRecordRepository.saveAll(records);

        // Compteurs
        ipmFile.setNbTransactions(eligible.size());
        ipmFile.setTotalAmount(totalAmount);

        // Écrire les fichiers
        try {
            Files.createDirectories(Paths.get(baseDir));
            Path asciiPath  = Paths.get(baseDir, fileName + "_cb.txt");
            Path binaryPath = Paths.get(baseDir, fileName + "_cb.ipm");
            Files.write(asciiPath, ascii.toString().getBytes());
            Files.write(binaryPath, ascii.toString().getBytes());
            ipmFile.setFilePathAscii(asciiPath.toString());
            ipmFile.setFilePathBinary(binaryPath.toString());

            // Journal + checksum
            String checksum = sha256(ascii.toString());
            IpmProcessingLog plog = new IpmProcessingLog();
            plog.setFileId(ipmFile.getFileId());
            plog.setFileName(fileName);
            plog.setFilePath(asciiPath.toString());
            plog.setRole("ISSUER");
            plog.setDirection("OUT");
            plog.setAction("GENERATED");
            plog.setExecutionId(executionId);
            plog.setRecordCount(eligible.size());
            plog.setChecksum(checksum);
            plog.setStatus("DONE");
            logRepository.save(plog);

        } catch (IOException e) {
            log.error("[DMCS-ISS] Error writing files: {}", e.getMessage());
            ipmFile.setStatus("ERROR");
        }

        ipmFile = issIpmFileRepository.save(ipmFile);
        log.info("[DMCS-ISS] Chargeback file {} generated — {} chargebacks, total={}",
                ipmFile.getFileId(), eligible.size(), totalAmount);
        return ipmFile;
    }

    private String safe(String s) { return s != null ? s : ""; }

    private String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format("%02X", b));
        return sb.toString();
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
