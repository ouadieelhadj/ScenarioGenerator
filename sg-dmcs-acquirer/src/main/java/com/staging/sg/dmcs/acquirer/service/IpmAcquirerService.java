package com.staging.sg.dmcs.acquirer.service;

import com.staging.sg.common.entity.AcqAuthorization;
import com.staging.sg.common.entity.AcqReversal;
import com.staging.sg.common.entity.AcqAdvice;
import com.staging.sg.common.entity.AcqIpmFile;
import com.staging.sg.common.entity.AcqIpmRecord;
import com.staging.sg.common.repository.AcqAuthorizationRepository;
import com.staging.sg.common.repository.AcqReversalRepository;
import com.staging.sg.common.repository.AcqAdviceRepository;
import com.staging.sg.common.repository.AcqIpmFileRepository;
import com.staging.sg.common.repository.AcqIpmRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.transaction.annotation.Transactional;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
@ConditionalOnProperty(name = "dmcs.legacy.enabled", havingValue = "true")
public class IpmAcquirerService {

    private static final Logger log = LoggerFactory.getLogger(IpmAcquirerService.class);

    private final AcqAuthorizationRepository acqAuthRepository;
    private final AcqReversalRepository      acqReversalRepository;
    private final AcqAdviceRepository        acqAdviceRepository;
    private final AcqIpmFileRepository          ipmFileRepository;
    private final AcqIpmRecordRepository        ipmRecordRepository;

    @Value("${dmcs.base-dir:D:/MoneyCore/ScenarioGenerator/dmcs}")
    private String baseDir;

    @Value("${mc.acquirer.defaults.DE032_ACQUIRING_BIN:411111}")
    private String acquiringBin;

    @Value("${dmcs.file-id-prefix:MC}")
    private String fileIdPrefix;

    public IpmAcquirerService(AcqAuthorizationRepository acqAuthRepository,
                                AcqReversalRepository acqReversalRepository,
                                AcqAdviceRepository acqAdviceRepository,
                                AcqIpmFileRepository ipmFileRepository,
                                AcqIpmRecordRepository ipmRecordRepository) {
        this.acqAuthRepository    = acqAuthRepository;
        this.acqReversalRepository = acqReversalRepository;
        this.acqAdviceRepository  = acqAdviceRepository;
        this.ipmFileRepository   = ipmFileRepository;
        this.ipmRecordRepository = ipmRecordRepository;
    }

    // ── Generate IPM File ────────────────────────────────────

    @Transactional
    public AcqIpmFile generate(Long executionId, LocalDate date, String createdBy) {
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

        // Load reversals + advices for this execution
        List<AcqReversal> reversals = executionId != null
                ? acqReversalRepository.findByExecutionId(executionId)
                : java.util.Collections.emptyList();
        List<AcqAdvice> advices = executionId != null
                ? acqAdviceRepository.findByExecutionId(executionId)
                : java.util.Collections.emptyList();
        log.info("[DMCS-GEN] Found {} reversals, {} advices", reversals.size(), advices.size());


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

        // Create AcqIpmFile entity
        AcqIpmFile ipmFile = new AcqIpmFile();
        ipmFile.setFileName(ts);
        ipmFile.setFileDate(fileDate);
        ipmFile.setFileId(fileId);
        ipmFile.setCreatedBy(createdBy);
        ipmFile.setFilePathBinary(binaryPath.toString());
        ipmFile.setFilePathAscii(asciiPath.toString());
        int totalTx = authorizations.size() + reversals.size() + advices.size();
        ipmFile.setNbTransactions(totalTx);
        ipmFile.setTotalAmount(totalAmount);
        ipmFile.setTotalAmountCurrency("978");
        ipmFile = ipmFileRepository.save(ipmFile);

        // Build records
        List<AcqIpmRecord> records = new ArrayList<>();
        int msgNum = 1;

        // Header
        records.add(buildHeader(ipmFile, fileId, msgNum++));

        // Presentments
        for (AcqAuthorization auth : authorizations) {
            records.add(buildPresentment(ipmFile, auth, msgNum++));
            records.add(buildAddendum(ipmFile, auth, msgNum++));
        }
        // Reversals -> 1240
        for (AcqReversal rev : reversals) {
            records.add(buildReversalPresentment(ipmFile, rev, msgNum++));
        }
        // Advices -> 1240
        for (AcqAdvice adv : advices) {
            records.add(buildAdvicePresentment(ipmFile, adv, msgNum++));
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

    private AcqIpmRecord buildHeader(AcqIpmFile ipmFile, String fileId, int msgNum) {
        AcqIpmRecord r = new AcqIpmRecord();
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

    private AcqIpmRecord buildPresentment(AcqIpmFile ipmFile,
                                        AcqAuthorization auth, int msgNum) {
        AcqIpmRecord r = new AcqIpmRecord();
        r.setIpmFile(ipmFile);
        r.setAcqAuthId(auth.getId());
        r.setMessageNumber(msgNum);
        r.setRecordType("PRESENTMENT");
        r.setMti("1240");
        r.setFunctionCode("200");
        r.setDe002Pan(auth.getDe002PanRaw() != null ? auth.getDe002PanRaw() : auth.getDe002Pan());
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
        // Priority 1 — Mandatory IPM fields
        r.setDe005AmountRecon(auth.getDe004Amount());           // DE005 = DE004 (same currency)
        r.setDe050CurrencyRecon(auth.getDe049Currency());       // DE050 = DE049
        r.setDe031AcqRefData(buildAcqRefData(auth, msgNum));    // DE031 ARN 23 pos
        r.setDe063NetworkData(auth.getDe037Rrn());              // DE063 = RRN trace
        String ascii = String.format(
            "1240|200|PAN=%s|PC=%s|AMT=%012d|DT=%s|MCC=%s|" +
            "ACQ=%s|RRN=%s|AUTH=%s|TID=%s|MID=%s|CCY=%s|" +
            "AMT_RECON=%012d|CCY_RECON=%s|ARN=%s|NET=%s|MSG=%08d",
            safe(auth.getDe002PanRaw() != null ? auth.getDe002PanRaw() : auth.getDe002Pan()),
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
            auth.getDe004Amount() != null ? auth.getDe004Amount() : 0,
            safe(auth.getDe049Currency()),
            buildAcqRefData(auth, msgNum),
            safe(auth.getDe037Rrn()),
            msgNum);
        r.setRawAscii(ascii);
        r.setRawHex(toHex(ascii.getBytes()));
        return r;
    }

    // ── Build Presentment from Reversal 0400 (Function 200 + reason) ──
    private AcqIpmRecord buildReversalPresentment(AcqIpmFile ipmFile,
                                                AcqReversal rev, int msgNum) {
        AcqIpmRecord r = new AcqIpmRecord();
        r.setIpmFile(ipmFile);
        r.setMessageNumber(msgNum);
        r.setRecordType("PRESENTMENT_REV");
        r.setMti("1240");
        r.setFunctionCode("200");
        r.setDe002Pan(rev.getDe002Pan());
        r.setDe003ProcCode(rev.getDe003ProcCode());
        r.setDe004Amount(rev.getDe004Amount());
        r.setDe024FuncCode("200");
        r.setDe025Reason("4000");        // Full reversal reason
        r.setDe037Rrn(rev.getDe037Rrn());
        r.setDe038AuthCode(rev.getDe038AuthCode());
        r.setDe041TermId(rev.getDe041TermId());
        r.setDe042MerchId(rev.getDe042MerchId());
        r.setDe049Currency(rev.getDe049Currency());
        r.setDe071MsgNum(String.format("%08d", msgNum));
        r.setDe005AmountRecon(rev.getDe004Amount());
        r.setDe050CurrencyRecon(rev.getDe049Currency());
        r.setDe063NetworkData(rev.getDe037Rrn());
        String ascii = String.format(
            "1240|200|TYPE=REVERSAL|PAN=%s|PC=%s|AMT=%012d|RRN=%s|" +
            "AUTH=%s|TID=%s|MID=%s|CCY=%s|REASON=4000|MSG=%08d",
            safe(rev.getDe002Pan()), safe(rev.getDe003ProcCode()),
            rev.getDe004Amount() != null ? rev.getDe004Amount() : 0,
            safe(rev.getDe037Rrn()), safe(rev.getDe038AuthCode()),
            safe(rev.getDe041TermId()), safe(rev.getDe042MerchId()),
            safe(rev.getDe049Currency()), msgNum);
        r.setRawAscii(ascii);
        r.setRawHex(toHex(ascii.getBytes()));
        return r;
    }

    // ── Build Presentment from Advice 0120 (Function 200) ──
    private AcqIpmRecord buildAdvicePresentment(AcqIpmFile ipmFile,
                                              AcqAdvice adv, int msgNum) {
        AcqIpmRecord r = new AcqIpmRecord();
        r.setIpmFile(ipmFile);
        r.setMessageNumber(msgNum);
        r.setRecordType("PRESENTMENT_ADV");
        r.setMti("1240");
        r.setFunctionCode("200");
        r.setDe002Pan(adv.getDe002Pan());
        r.setDe003ProcCode(adv.getDe003ProcCode());
        r.setDe004Amount(adv.getDe004Amount());
        r.setDe024FuncCode("200");
        r.setDe025Reason("00");
        r.setDe037Rrn(adv.getDe037Rrn());
        r.setDe038AuthCode(adv.getDe038AuthCode());
        r.setDe049Currency(adv.getDe049Currency());
        r.setDe071MsgNum(String.format("%08d", msgNum));
        r.setDe005AmountRecon(adv.getDe004Amount());
        r.setDe050CurrencyRecon(adv.getDe049Currency());
        r.setDe063NetworkData(adv.getDe037Rrn());
        String ascii = String.format(
            "1240|200|TYPE=ADVICE|PAN=%s|PC=%s|AMT=%012d|RRN=%s|" +
            "AUTH=%s|CCY=%s|REASON=%s|MSG=%08d",
            safe(adv.getDe002Pan()), safe(adv.getDe003ProcCode()),
            adv.getDe004Amount() != null ? adv.getDe004Amount() : 0,
            safe(adv.getDe037Rrn()), safe(adv.getDe038AuthCode()),
            safe(adv.getDe049Currency()), safe(adv.getDe060Reason()), msgNum);
        r.setRawAscii(ascii);
        r.setRawHex(toHex(ascii.getBytes()));
        return r;
    }
    // ── Build 1644 Financial Detail Addendum (function 696) ──
    // Complète un 1240 First Presentment avec des données métier (PDS).
    private AcqIpmRecord buildAddendum(AcqIpmFile ipmFile,
                                       AcqAuthorization auth, int msgNum) {
        AcqIpmRecord r = new AcqIpmRecord();
        r.setIpmFile(ipmFile);
        r.setAcqAuthId(auth.getId());
        r.setMessageNumber(msgNum);
        r.setRecordType("ADDENDUM");
        r.setMti("1644");
        r.setFunctionCode("696");
        r.setDe002Pan(auth.getDe002PanRaw() != null ? auth.getDe002PanRaw() : auth.getDe002Pan());
        r.setDe004Amount(auth.getDe004Amount());
        r.setDe024FuncCode("696");
        r.setDe037Rrn(auth.getDe037Rrn());
        r.setDe049Currency(auth.getDe049Currency());
        r.setDe071MsgNum(String.format("%08d", msgNum));
        // PDS : 0501 Usage Code + 0502 Custom Identifier (RRN comme lien)
        String pds = com.staging.sg.common.iso.PdsUtil.concat(
            com.staging.sg.common.iso.PdsUtil.pds0501UsageCode("696"),
            com.staging.sg.common.iso.PdsUtil.encode(502, safe(auth.getDe037Rrn()))
        );
        r.setPdsData(pds);
        String ascii = String.format(
            "1644|696|TYPE=ADDENDUM|PAN=%s|AMT=%012d|RRN=%s|CCY=%s|PDS=%s|MSG=%08d",
            safe(auth.getDe002PanRaw() != null ? auth.getDe002PanRaw() : auth.getDe002Pan()),
            auth.getDe004Amount() != null ? auth.getDe004Amount() : 0,
            safe(auth.getDe037Rrn()),
            safe(auth.getDe049Currency()),
            pds, msgNum);
        r.setRawAscii(ascii);
        r.setRawHex(toHex(ascii.getBytes()));
        return r;
    }


    private AcqIpmRecord buildTrailer(AcqIpmFile ipmFile, String fileId,
                                    int msgNum, int txCount, long totalAmount) {
        AcqIpmRecord r = new AcqIpmRecord();
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

    private void writeBinaryFile(Path path, List<AcqIpmRecord> records) {
        try (DataOutputStream dos = new DataOutputStream(
                new BufferedOutputStream(
                        new FileOutputStream(path.toFile())))) {
            for (AcqIpmRecord r : records) {
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

    private void writeAsciiFile(Path path, List<AcqIpmRecord> records,
                                 AcqIpmFile ipmFile) {
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
            for (AcqIpmRecord r : records) {
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

    public List<AcqIpmFile> listFiles() {
        return ipmFileRepository.findAllByOrderByGenerationDateDesc();
    }

    public AcqIpmFile getFile(Long id) {
        return ipmFileRepository.findById(id).orElse(null);
    }

    public List<AcqIpmRecord> getRecords(Long fileId) {
        return ipmRecordRepository.findByIpmFileId(fileId);
    }

    // ── Helpers ───────────────────────────────────────────────

    private String generateFileId(LocalDate date) {
        String d = date.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        long count = ipmFileRepository.findByFileDate(date).size() + 1;
        return String.format("%s%s%03d", fileIdPrefix, d, count);
    }

    // ── DE031 Acquirer Reference Data (23 positions) ────────
    private String buildAcqRefData(AcqAuthorization auth, int msgNum) {
        // Pos 1 : Mixed use (0)
        // Pos 2-7 : Acquirer BIN (6 digits)
        // Pos 8-11 : Julian date YDDD
        // Pos 12-22 : Sequence (11 digits)
        // Pos 23 : Check digit
        String acqBin = safe(auth.getDe032AcqId());
        if (acqBin.length() > 6) acqBin = acqBin.substring(0, 6);
        acqBin = String.format("%6s", acqBin).replace(" ", "0");
        java.time.LocalDate now = java.time.LocalDate.now();
        int year = now.getYear() % 10;
        int doy  = now.getDayOfYear();
        String julian = String.format("%d%03d", year, doy);
        String seq = String.format("%011d", msgNum);
        String base = "0" + acqBin + julian + seq;
        if (base.length() > 22) base = base.substring(0, 22);
        base = String.format("%-22s", base).replace(" ", "0");
        int checkDigit = computeLuhn(base);
        return base + checkDigit;
    }

    private int computeLuhn(String num) {
        int sum = 0; boolean alt = false;
        for (int i = num.length() - 1; i >= 0; i--) {
            int d = Character.getNumericValue(num.charAt(i));
            if (alt) { d *= 2; if (d > 9) d -= 9; }
            sum += d; alt = !alt;
        }
        return (10 - (sum % 10)) % 10;
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
