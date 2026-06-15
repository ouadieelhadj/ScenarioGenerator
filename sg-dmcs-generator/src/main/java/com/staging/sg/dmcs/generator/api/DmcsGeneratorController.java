package com.staging.sg.dmcs.generator.api;

import com.staging.sg.common.entity.IpmFile;
import com.staging.sg.common.entity.IpmRecord;
import com.staging.sg.dmcs.generator.service.IpmGeneratorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/dmcs")
public class DmcsGeneratorController {

    private static final Logger log =
            LoggerFactory.getLogger(DmcsGeneratorController.class);

    private final IpmGeneratorService generatorService;

    public DmcsGeneratorController(IpmGeneratorService generatorService) {
        this.generatorService = generatorService;
    }

    // POST /api/dmcs/generate
    // Paramètres : executionId (optionnel), date (optionnel)
    @PostMapping("/generate")
    public ResponseEntity<?> generate(
            @RequestParam(required = false) Long executionId,
            @RequestParam(required = false) String date) {
        try {
            LocalDate fileDate = date != null
                    ? LocalDate.parse(date) : LocalDate.now();

            IpmFile ipmFile = generatorService.generate(
                    executionId, fileDate, "system");

            if (ipmFile == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error",
                                "No approved authorizations found"));
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("fileId",         ipmFile.getId());
            result.put("fileName",       ipmFile.getFileName());
            result.put("ipmFileId",      ipmFile.getFileId());
            result.put("date",           ipmFile.getFileDate());
            result.put("nbTransactions", ipmFile.getNbTransactions());
            result.put("totalAmount",    ipmFile.getTotalAmount());
            result.put("status",         ipmFile.getStatus());
            result.put("binaryPath",     ipmFile.getFilePathBinary());
            result.put("asciiPath",      ipmFile.getFilePathAscii());
            result.put("links", Map.of(
                    "records", "/api/dmcs/files/" + ipmFile.getId() + "/records",
                    "binary",  "/api/dmcs/files/" + ipmFile.getId() + "/download/binary",
                    "ascii",   "/api/dmcs/files/" + ipmFile.getId() + "/download/ascii"
            ));

            log.info("[DMCS-GEN] File generated — id={} tx={}",
                    ipmFile.getId(), ipmFile.getNbTransactions());

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("[DMCS-GEN] Error : {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", e.getMessage()));
        }
    }

    // GET /api/dmcs/files
    @GetMapping("/files")
    public ResponseEntity<List<IpmFile>> listFiles() {
        return ResponseEntity.ok(generatorService.listFiles());
    }

    // GET /api/dmcs/files/{id}
    @GetMapping("/files/{id}")
    public ResponseEntity<?> getFile(@PathVariable Long id) {
        IpmFile file = generatorService.getFile(id);
        if (file == null)
            return ResponseEntity.notFound().build();
        return ResponseEntity.ok(file);
    }

    // GET /api/dmcs/files/{id}/records
    @GetMapping("/files/{id}/records")
    public ResponseEntity<List<IpmRecord>> getRecords(@PathVariable Long id) {
        return ResponseEntity.ok(generatorService.getRecords(id));
    }

    // GET /api/dmcs/files/{id}/download/binary
    @GetMapping("/files/{id}/download/binary")
    public ResponseEntity<Resource> downloadBinary(@PathVariable Long id) {
        IpmFile file = generatorService.getFile(id);
        if (file == null || file.getFilePathBinary() == null)
            return ResponseEntity.notFound().build();
        try {
            Resource resource = new FileSystemResource(
                    Paths.get(file.getFilePathBinary()));
            if (!Files.exists(Paths.get(file.getFilePathBinary())))
                return ResponseEntity.notFound().build();
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" +
                            file.getFileName() + ".ipm\"")
                    .body(resource);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    // GET /api/dmcs/files/{id}/download/ascii
    @GetMapping("/files/{id}/download/ascii")
    public ResponseEntity<Resource> downloadAscii(@PathVariable Long id) {
        IpmFile file = generatorService.getFile(id);
        if (file == null || file.getFilePathAscii() == null)
            return ResponseEntity.notFound().build();
        try {
            Resource resource = new FileSystemResource(
                    Paths.get(file.getFilePathAscii()));
            if (!Files.exists(Paths.get(file.getFilePathAscii())))
                return ResponseEntity.notFound().build();
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_PLAIN)
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" +
                            file.getFileName() + ".txt\"")
                    .body(resource);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    // GET /api/dmcs/status
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("application", "SG DMCS Generator");
        body.put("version",     "1.0.0-SNAPSHOT");
        body.put("port",        8082);
        body.put("endpoints", Map.of(
                "generate", "POST /api/dmcs/generate?executionId=X&date=yyyy-MM-dd",
                "files",    "GET  /api/dmcs/files",
                "records",  "GET  /api/dmcs/files/{id}/records",
                "binary",   "GET  /api/dmcs/files/{id}/download/binary",
                "ascii",    "GET  /api/dmcs/files/{id}/download/ascii"
        ));
        return ResponseEntity.ok(body);
    }
}
