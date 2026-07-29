package com.staging.sg.dmcs.issuer.service;

import com.staging.sg.common.entity.DmcsIssuerClearingTransaction;
import com.staging.sg.common.entity.IpmProcessingLog;
import com.staging.sg.common.entity.IssIpmFile;
import com.staging.sg.common.entity.IssIpmRecord;
import com.staging.sg.common.repository.DmcsIssuerClearingTransactionRepository;
import com.staging.sg.common.repository.IpmProcessingLogRepository;
import com.staging.sg.common.repository.IssIpmFileRepository;
import com.staging.sg.common.repository.IssIpmRecordRepository;
import com.staging.sg.common.service.DmcIncomingMessageMapper;
import com.staging.sg.dmcs.common.ipm.DmcIpmFileCodec;
import com.staging.sg.dmcs.common.ipm.DmcIpmFileValidator;
import com.staging.sg.dmcs.common.ipm.DmcIpmPackager;
import org.jpos.iso.ISOMsg;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

@Service
public class IpmReaderService {
    private static final DateTimeFormatter FILE_DATE = DateTimeFormatter.ofPattern("yyMMdd");

    private final IssIpmFileRepository fileRepository;
    private final IssIpmRecordRepository recordRepository;
    private final DmcsIssuerClearingTransactionRepository clearingRepository;
    private final IpmProcessingLogRepository logRepository;

    public IpmReaderService(
            IssIpmFileRepository fileRepository,
            IssIpmRecordRepository recordRepository,
            DmcsIssuerClearingTransactionRepository clearingRepository,
            IpmProcessingLogRepository logRepository) {
        this.fileRepository = fileRepository;
        this.recordRepository = recordRepository;
        this.clearingRepository = clearingRepository;
        this.logRepository = logRepository;
    }

    @Transactional
    public IssIpmFile readFile(String filePath) throws Exception {
        Path path = Paths.get(filePath);
        if (!Files.isRegularFile(path)) throw new IllegalArgumentException("File not found: " + filePath);
        byte[] content = Files.readAllBytes(path);
        String checksum = sha256(content);
        String fileName = path.getFileName().toString();
        if (logRepository.findByChecksumAndRoleAndDirection(checksum, "ISSUER", "IN").isPresent()) {
            throw new IllegalStateException("File already processed: " + fileName);
        }

        List<ISOMsg> messages = new DmcIpmFileCodec(new DmcIpmPackager())
                .read(new ByteArrayInputStream(content));
        var validation = DmcIpmFileValidator.validate(messages);
        LocalDate businessDate = fileDate(validation.fileId());

        IssIpmFile file = new IssIpmFile();
        file.setFileName(fileName);
        file.setFileDate(businessDate);
        file.setDirection("IN");
        file.setStatus("READ");
        file.setProcessingMode("T".equals(validation.processingMode()) ? "TEST" : "PRODUCTION");
        file.setFilePathBinary(path.toAbsolutePath().normalize().toString());
        file.setFileId(validation.fileId());
        file.setTotalAmount(validation.amountChecksum());
        file = fileRepository.save(file);

        List<IssIpmRecord> records = new ArrayList<>();
        int transactions = 0;
        for (int i = 0; i < messages.size(); i++) {
            ISOMsg message = messages.get(i);
            records.add(toRecord(file, message, i + 1));
            if (DmcIncomingMessageMapper.isSupportedLifecycle(message)) {
                var clearing = DmcIncomingMessageMapper.populate(
                        new DmcsIssuerClearingTransaction(), message,
                        businessDate, file.getId(), i + 1);
                clearingRepository.save(clearing);
                transactions++;
            }
        }
        recordRepository.saveAll(records);
        file.setNbTransactions(transactions);
        file = fileRepository.save(file);
        logRepository.save(processingLog(
                file.getFileId(), fileName, filePath, checksum, messages.size()));
        return file;
    }

    private static IssIpmRecord toRecord(IssIpmFile file, ISOMsg message, int number)
            throws Exception {
        IssIpmRecord record = new IssIpmRecord();
        record.setIpmFile(file);
        record.setDirection("IN");
        record.setMessageNumber(number);
        record.setMti(message.getMTI());
        record.setFunctionCode(message.getString(24));
        record.setDe024FuncCode(message.getString(24));
        record.setRecordType(recordType(message));
        record.setDe002Pan(message.getString(2));
        record.setDe003ProcCode(message.getString(3));
        if (message.hasField(4)) record.setDe004Amount(Long.parseLong(message.getString(4)));
        record.setDe012LocalDt(message.getString(12));
        record.setDe022PosCode(message.getString(22));
        record.setDe026Mcc(message.getString(26));
        record.setDe031AcqRefData(message.getString(31));
        record.setDe032AcqId(message.getString(32));
        record.setDe037Rrn(message.getString(37));
        record.setDe038AuthCode(message.getString(38));
        record.setDe041TermId(message.getString(41));
        record.setDe042MerchId(message.getString(42));
        record.setDe043MerchName(truncate(message.getString(43), 40));
        record.setDe049Currency(message.getString(49));
        record.setDe071MsgNum(message.getString(71));
        record.setDe093DestId(message.getString(93));
        record.setDe094OriginId(message.getString(94));
        record.setPdsData(message.getString(48));
        record.setRawHex(HexFormat.of().withUpperCase().formatHex(message.pack()));
        return record;
    }

    private static String recordType(ISOMsg message) throws Exception {
        String key = message.getMTI() + "/" + message.getString(24);
        return switch (key) {
            case "1644/697" -> "HEADER";
            case "1644/695" -> "TRAILER";
            case "1240/200" -> "PRESENTMENT";
            case "1240/205", "1240/282" -> "REPRESENTMENT";
            case "1442/450", "1442/453" -> "CHARGEBACK";
            default -> "OTHER";
        };
    }

    private static IpmProcessingLog processingLog(
            String fileId, String fileName, String path, String checksum, int count) {
        IpmProcessingLog log = new IpmProcessingLog();
        log.setFileId(fileId);
        log.setFileName(fileName);
        log.setFilePath(path);
        log.setRole("ISSUER");
        log.setDirection("IN");
        log.setAction("READ");
        log.setRecordCount(count);
        log.setChecksum(checksum);
        log.setStatus("DONE");
        return log;
    }

    private static LocalDate fileDate(String fileId) {
        return LocalDate.parse(fileId.substring(3, 9), FILE_DATE);
    }

    private static String sha256(byte[] content) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
    }

    private static String truncate(String value, int length) {
        return value != null && value.length() > length ? value.substring(0, length) : value;
    }
}
