package com.staging.sg.dmcs.reader.service;

import com.staging.sg.common.entity.IpmFile;
import com.staging.sg.common.entity.IpmRecord;
import com.staging.sg.common.repository.IpmFileRepository;
import com.staging.sg.common.repository.IpmRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;

@Service
public class IpmReaderService {

    private static final Logger log = LoggerFactory.getLogger(IpmReaderService.class);

    private final IpmFileRepository   ipmFileRepository;
    private final IpmRecordRepository ipmRecordRepository;

    @Value("${dmcs.base-dir:D:/MoneyCore/ScenarioGenerator/dmcs}")
    private String baseDir;

    public IpmReaderService(IpmFileRepository ipmFileRepository,
                             IpmRecordRepository ipmRecordRepository) {
        this.ipmFileRepository   = ipmFileRepository;
        this.ipmRecordRepository = ipmRecordRepository;
    }

    // ── List all IPM files ────────────────────────────────────

    public List<IpmFile> listFiles() {
        return ipmFileRepository.findAllByOrderByGenerationDateDesc();
    }

    public List<IpmFile> listFilesByDate(LocalDate date) {
        return ipmFileRepository.findByFileDate(date);
    }

    // ── Get file details ──────────────────────────────────────

    public IpmFile getFile(Long id) {
        return ipmFileRepository.findById(id).orElse(null);
    }

    // ── Get records ───────────────────────────────────────────

    public List<IpmRecord> getRecords(Long fileId) {
        return ipmRecordRepository.findByIpmFileId(fileId);
    }

    public List<IpmRecord> getRecordsByType(Long fileId, String recordType) {
        return ipmRecordRepository.findByIpmFileIdAndRecordType(fileId, recordType);
    }

    public List<IpmRecord> getPresentments(Long fileId) {
        return ipmRecordRepository.findByIpmFileIdAndRecordType(fileId, "PRESENTMENT");
    }

    // ── Read binary file ──────────────────────────────────────

    public List<Map<String, Object>> readBinaryFile(Long fileId) {
        IpmFile file = getFile(fileId);
        if (file == null || file.getFilePathBinary() == null) return List.of();

        List<Map<String, Object>> records = new ArrayList<>();
        Path path = Paths.get(file.getFilePathBinary());

        if (!Files.exists(path)) {
            log.warn("[DMCS-READ] Binary file not found : {}", path);
            return List.of();
        }

        try (DataInputStream dis = new DataInputStream(
                new BufferedInputStream(new FileInputStream(path.toFile())))) {

            int msgNum = 0;
            while (dis.available() > 0) {
                int len  = dis.readInt();
                byte[] data = new byte[len];
                dis.readFully(data);

                String ascii = new String(data);
                Map<String, Object> record = parseAsciiRecord(ascii, ++msgNum);
                records.add(record);
            }

            log.info("[DMCS-READ] Binary file read — {} records", records.size());

        } catch (Exception e) {
            log.error("[DMCS-READ] Error reading binary : {}", e.getMessage(), e);
        }

        return records;
    }

    // ── Read ASCII file ───────────────────────────────────────

    public List<Map<String, Object>> readAsciiFile(Long fileId) {
        IpmFile file = getFile(fileId);
        if (file == null || file.getFilePathAscii() == null) return List.of();

        List<Map<String, Object>> records = new ArrayList<>();
        Path path = Paths.get(file.getFilePathAscii());

        if (!Files.exists(path)) {
            log.warn("[DMCS-READ] ASCII file not found : {}", path);
            return List.of();
        }

        try (BufferedReader br = new BufferedReader(new FileReader(path.toFile()))) {
            String line;
            int msgNum = 0;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.startsWith("1240|") ||
                    line.startsWith("1644|")) {
                    Map<String, Object> record = parseAsciiRecord(line, ++msgNum);
                    records.add(record);
                }
            }
            log.info("[DMCS-READ] ASCII file read — {} records", records.size());

        } catch (Exception e) {
            log.error("[DMCS-READ] Error reading ASCII : {}", e.getMessage(), e);
        }

        return records;
    }

    // ── Parse ASCII record ────────────────────────────────────

    private Map<String, Object> parseAsciiRecord(String line, int msgNum) {
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("lineNumber", msgNum);
        record.put("raw", line);

        if (line == null || line.isEmpty()) return record;

        String[] parts = line.split("\\|");
        if (parts.length < 2) return record;

        String mti  = parts[0];
        String func = parts[1];
        record.put("mti",          mti);
        record.put("functionCode", func);

        // Determine record type
        if ("1644".equals(mti) && "685".equals(func)) {
            record.put("recordType", "HEADER");
        } else if ("1644".equals(mti) && "686".equals(func)) {
            record.put("recordType", "TRAILER");
        } else if ("1240".equals(mti) && "200".equals(func)) {
            record.put("recordType", "PRESENTMENT");
        } else {
            record.put("recordType", "UNKNOWN");
        }

        // Parse key=value pairs
        for (int i = 2; i < parts.length; i++) {
            String[] kv = parts[i].split("=", 2);
            if (kv.length == 2) {
                record.put(kv[0], kv[1]);
            }
        }

        return record;
    }

    // ── Stats ─────────────────────────────────────────────────

    public Map<String, Object> getStats(Long fileId) {
        Map<String, Object> stats = new LinkedHashMap<>();
        IpmFile file = getFile(fileId);
        if (file == null) return stats;

        List<IpmRecord> records    = getRecords(fileId);
        List<IpmRecord> presentments = getPresentments(fileId);

        stats.put("fileId",          file.getFileId());
        stats.put("fileName",        file.getFileName());
        stats.put("date",            file.getFileDate());
        stats.put("status",          file.getStatus());
        stats.put("nbTransactions",  file.getNbTransactions());
        stats.put("totalAmount",     file.getTotalAmount());
        stats.put("totalRecords",    records.size());
        stats.put("presentments",    presentments.size());
        stats.put("generationDate",  file.getGenerationDate());

        // Amount by currency
        Map<String, Long> byCurrency = new LinkedHashMap<>();
        for (IpmRecord r : presentments) {
            String ccy = r.getDe049Currency() != null ? r.getDe049Currency() : "N/A";
            Long   amt = r.getDe004Amount()   != null ? r.getDe004Amount()   : 0L;
            byCurrency.merge(ccy, amt, Long::sum);
        }
        stats.put("amountByCurrency", byCurrency);

        return stats;
    }

    // ── List files in dmcs directory ──────────────────────────

    public List<Map<String, Object>> listDmcsDirectory() {
        List<Map<String, Object>> files = new ArrayList<>();
        try {
            Path dir = Paths.get(baseDir);
            if (!Files.exists(dir)) return files;

            Files.list(dir).forEach(p -> {
                Map<String, Object> f = new LinkedHashMap<>();
                f.put("name", p.getFileName().toString());
                f.put("path", p.toString());
                try {
                    f.put("size", Files.size(p));
                    f.put("lastModified", Files.getLastModifiedTime(p).toString());
                } catch (Exception ignored) {}
                files.add(f);
            });
        } catch (Exception e) {
            log.error("[DMCS-READ] Error listing dir : {}", e.getMessage());
        }
        return files;
    }
}
