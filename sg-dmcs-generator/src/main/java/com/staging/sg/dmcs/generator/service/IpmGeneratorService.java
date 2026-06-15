package com.staging.sg.dmcs.generator.service;

import com.staging.sg.common.entity.AcqAuthorization;
import com.staging.sg.common.entity.IpmFile;
import com.staging.sg.common.entity.IpmRecord;
import com.staging.sg.common.repository.AcqAuthorizationRepository;
import com.staging.sg.common.repository.IpmFileRepository;
import com.staging.sg.common.repository.IpmRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
public class IpmGeneratorService {

    private static final Logger log = LoggerFactory.getLogger(IpmGeneratorService.class);

    private final AcqAuthorizationRepository acqAuthRepository;
    private final IpmFileRepository          ipmFileRepository;
    private final IpmRecordRepository        ipmRecordRepository;

    @Value("${dmcs.base-dir:D:/MoneyCore/ScenarioGenerator/dmcs}")
    private String baseDir;

    @Value("${mc.acquirer.defaults.DE032_ACQUIRING_BIN:411111}")
    private String acquiringBin;

    @Value("${dmcs.file-id-prefix:MC}")
    private String fileIdPrefix;

    public IpmGeneratorService(AcqAuthorizationRepository acqAuthRepository,
                                IpmFileRepository ipmFileRepository,
                                IpmRecordRepository ipmRecordRepository) {
        this.acqAuthRepository   = acqAuthRepository;
        this.ipmFileRepository   = ipmFileRepository;
        this.ipmRecordRepository = ipmRecordRepository;
    }

    // ── Generate IPM File ────────────────────────────────────

    @Transactional
    public IpmFile generate(Long executionId, LocalDate date, String createdBy) {
        log.info("[DMCS-GEN] Generating IPM — executionId={} date={}", executionId, date);

        // Load approved authorizations
        List<AcqAuthorization> authorizations;
        if (executionId != null) {
            authorizations = acqAuthRepository
                    .findByExecutionIdAndApprovedTrue(executionId);
        } else {
            authorizations = acqAuthRepository
                    .findByApprovedTrueAndDe039Response("00");
        }

        log.info("[DMCS-GEN] Found {} approved authorizations", authorizations.size());

        if (authorizations.isEmpty()) {
            log.warn("[DMCS-GEN] No approved authorizations — skipping");
            return null;
        }

        // Create directories
        try { Files.createDirectories(Paths.get(baseDir)); }
        catch (Exception e) { log.error("[DMCS-GEN] Dir error : {}", e.getMessage()); }

        // File names
        String ts         = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        LocalDate fileDate = date != null ? date : LocalDate.now();
        String fileId      = generateFileId(fileDate);
        Path   binaryPath  = Paths.get(baseDir, ts + ".ipm");
        Path   asciiPath   = Paths.get(baseDir, ts + ".txt");

        // Total amount
        long totalAmount = authorizations.stream()
                .mapToLong(a -> a.getDe004Amount() != null ? a.getDe004Amount() : 0)
                .sum();

        // Create IpmFile entity
        IpmFile ipmFile = new IpmFile();
        ipmFile.setFileName(ts);
        ipmFile.setFileDate(fileDate);
        ipmFile.setFileId(fileId);
        ipmFile.setCreatedBy(createdBy);
        ipmFile.setFilePathBinary(binaryPath.toString());
        ipmFile.setFilePathAscii(asciiPath.toString());
        ipmFile.setNbTransactions(authorizations.size());
        ipmFile.setTotalAmount(totalAmount);
        ipmFile.setTotalAmountCurrency("978");
        ipmFile = ipmFileRepository.save(ipmFile);

        // Build records
        List<IpmRecord> records = new ArrayList<>();
        int msgNum = 1;

        // Header
        records.add(buildHeader(ipmFile, fileId, msgNum++));

        // Presentments
        for (AcqAuthorization auth : authorizations) {
            records.add(buildPresentment(ipmFile, auth, msgNum++));
        }

        // Trailer
        records.add(buildTrailer(ipmFile, fileId, msgNum,
                authorizations.size(), totalAmount));

        // Save records
        ipmRecordRepository.saveAll(records);

        // Write files
        writeBinaryFile(binaryPath, records);
        writeAsciiFile(asciiPath, records, ipmFile);

        // Update status
        ipmFile.setStatus("GENERATED");
        ipmFile = ipmFileRepository.save(ipmFile);

        log.info("[DMCS-GEN] Done — binary={} ascii={} tx={} amt={}",
                binaryPath, asciiPath, authorizations.size(), totalAmount);

        return ipmFile;
    }

    // ── Build records ─────────────────────────────────────────

    private IpmRecord buildHeader(IpmFile ipmFile, String fileId, int msgNum) {
        IpmRecord r = new IpmRecord();
        r.setIpmFile(ipmFile);
        r.setMessageNumber(msgNum);
        r.setRecordType("HEADER");
        r.setMti("1644");
        r.setFunctionCode("685");
        r.setDe024FuncCode("685");
        r.setDe071MsgNum(String.format("%08d", msgNum));
        String ascii = String.format(
            "1644|685|FILE_ID=%s|MSG_NUM=%08d|DATE=%s|ACQ=%s",
            fileId, msgNum,
            LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")),
            acquiringBin);
        r.setRawAscii(ascii);
        r.setRawHex(toHex(ascii.getBytes()));
        return r;
    }

    private IpmRecord buildPresentment(IpmFile ipmFile,
                                        AcqAuthorization auth, int msgNum) {
        IpmRecord r = new IpmRecord();
        r.setIpmFile(ipmFile);
        r.setAcqAuth(auth);
        r.setMessageNumber(msgNum);
        r.setRecordType("PRESENTMENT");
        r.setMti("1240");
        r.setFunctionCode("200");
        r.setDe002Pan(auth.getDe002Pan());
        r.setDe003ProcCode(auth.getDe003ProcCode());
        r.setDe004Amount(auth.getDe004Amount());
        r.setDe012LocalDt(safe(auth.getDe012LocalTime())
                + safe(auth.getDe013LocalDate()));
        r.setDe022PosCode(auth.getDe022PosMode());
        r.setDe024FuncCode("200");
        r.setDe025Reason("00");
        r.setDe026Mcc(auth.getDe018Mcc());
        r.setDe032AcqId(auth.getDe032AcqId());
        r.setDe037Rrn(auth.getDe037Rrn());
        r.setDe038AuthCode(auth.getDe038AuthCode());
        r.setDe041TermId(auth.getDe041TermId());
        r.setDe042MerchId(auth.getDe042MerchId());
        r.setDe043MerchName(auth.getDe043MerchName());
        r.setDe049Currency(auth.getDe049Currency());
        r.setDe071MsgNum(String.format("%08d", msgNum));
        String ascii = String.format(
            "1240|200|PAN=%s|PC=%s|AMT=%012d|DT=%s|MCC=%s|" +
            "ACQ=%s|RRN=%s|AUTH=%s|TID=%s|MID=%s|CCY=%s|MSG=%08d",
            safe(auth.getDe002Pan()),
            safe(auth.getDe003ProcCode()),
            auth.getDe004Amount() != null ? auth.getDe004Amount() : 0,
            safe(auth.getDe012LocalTime()) + safe(auth.getDe013LocalDate()),
            safe(auth.getDe018Mcc()),
            safe(auth.getDe032AcqId()),
            safe(auth.getDe037Rrn()),
            safe(auth.getDe038AuthCode()),
            safe(auth.getDe041TermId()),
            safe(auth.getDe042MerchId()),
            safe(auth.getDe049Currency()),
            msgNum);
        r.setRawAscii(ascii);
        r.setRawHex(toHex(ascii.getBytes()));
        return r;
    }

    private IpmRecord buildTrailer(IpmFile ipmFile, String fileId,
                                    int msgNum, int txCount, long totalAmount) {
        IpmRecord r = new IpmRecord();
        r.setIpmFile(ipmFile);
        r.setMessageNumber(msgNum);
        r.setRecordType("TRAILER");
        r.setMti("1644");
        r.setFunctionCode("686");
        r.setDe024FuncCode("686");
        r.setDe004Amount(totalAmount);
        r.setDe071MsgNum(String.format("%08d", msgNum));
        String ascii = String.format(
            "1644|686|FILE_ID=%s|MSG_NUM=%08d|TX_COUNT=%08d|TOTAL_AMT=%016d",
            fileId, msgNum, txCount, totalAmount);
        r.setRawAscii(ascii);
        r.setRawHex(toHex(ascii.getBytes()));
        return r;
    }

    // ── Write files ───────────────────────────────────────────

    private void writeBinaryFile(Path path, List<IpmRecord> records) {
        try (DataOutputStream dos = new DataOutputStream(
                new BufferedOutputStream(
                        new FileOutputStream(path.toFile())))) {
            for (IpmRecord r : records) {
                byte[] data = r.getRawHex() != null
                        ? fromHex(r.getRawHex())
                        : r.getRawAscii().getBytes();
                dos.writeInt(data.length);
                dos.write(data);
            }
            log.info("[DMCS-GEN] Binary written : {}", path);
        } catch (Exception e) {
            log.error("[DMCS-GEN] Binary error : {}", e.getMessage(), e);
        }
    }

    private void writeAsciiFile(Path path, List<IpmRecord> records,
                                 IpmFile ipmFile) {
        try (PrintWriter pw = new PrintWriter(new FileWriter(path.toFile()))) {
            pw.println("═══════════════════════════════════════════════════════");
            pw.println("  Mastercard IPM — Dual Message Clearing System");
            pw.println("═══════════════════════════════════════════════════════");
            pw.printf("  File ID   : %s%n", ipmFile.getFileId());
            pw.printf("  Date      : %s%n", ipmFile.getFileDate());
            pw.printf("  Generated : %s%n", ipmFile.getGenerationDate());
            pw.printf("  TX Count  : %d%n", ipmFile.getNbTransactions());
            pw.printf("  Total Amt : %d%n", ipmFile.getTotalAmount());
            pw.println("═══════════════════════════════════════════════════════");
            pw.println();
            for (IpmRecord r : records) {
                pw.println(r.getRawAscii());
            }
            pw.println();
            pw.println("═══════════════════════════════════════════════════════");
            pw.println("  END OF FILE");
            pw.println("═══════════════════════════════════════════════════════");
            log.info("[DMCS-GEN] ASCII written : {}", path);
        } catch (Exception e) {
            log.error("[DMCS-GEN] ASCII error : {}", e.getMessage(), e);
        }
    }

    // ── List files ────────────────────────────────────────────

    public List<IpmFile> listFiles() {
        return ipmFileRepository.findAllByOrderByGenerationDateDesc();
    }

    public IpmFile getFile(Long id) {
        return ipmFileRepository.findById(id).orElse(null);
    }

    public List<IpmRecord> getRecords(Long fileId) {
        return ipmRecordRepository.findByIpmFileId(fileId);
    }

    // ── Helpers ───────────────────────────────────────────────

    private String generateFileId(LocalDate date) {
        String d = date.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        long count = ipmFileRepository.findByFileDate(date).size() + 1;
        return String.format("%s%s%03d", fileIdPrefix, d, count);
    }

    private String safe(String s)  { return s != null ? s : ""; }

    private String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format("%02X", b));
        return sb.toString();
    }

    private byte[] fromHex(String hex) {
        int    len  = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2)
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        return data;
    }
}
