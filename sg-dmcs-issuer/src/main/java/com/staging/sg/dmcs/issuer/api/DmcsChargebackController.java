package com.staging.sg.dmcs.issuer.api;

import com.staging.sg.common.entity.IssIpmFile;
import com.staging.sg.common.entity.IssIpmRecord;
import com.staging.sg.common.repository.IssIpmFileRepository;
import com.staging.sg.common.repository.IssIpmRecordRepository;
import com.staging.sg.dmcs.issuer.service.IpmChargebackService;
import com.staging.sg.dmcs.issuer.service.IpmReaderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/dmcs")
public class DmcsChargebackController {
    private static final Logger log = LoggerFactory.getLogger(DmcsChargebackController.class);


    private final IpmChargebackService   chargebackService;
    private final IpmReaderService       readerService;
    private final IssIpmFileRepository    issIpmFileRepository;
    private final IssIpmRecordRepository  issIpmRecordRepository;

    public DmcsChargebackController(IpmChargebackService chargebackService,
                                    IpmReaderService readerService,
                                    IssIpmFileRepository issIpmFileRepository,
                                    IssIpmRecordRepository issIpmRecordRepository) {
        this.chargebackService     = chargebackService;
        this.readerService         = readerService;
        this.issIpmFileRepository  = issIpmFileRepository;
        this.issIpmRecordRepository = issIpmRecordRepository;
    }

    // GET /api/dmcs/status
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("application", "SG DMCS Issuer");
        body.put("version",     "1.0.0-SNAPSHOT");
        body.put("port",        8083);
        body.put("endpoints", Map.of(
                "chargeback", "POST /api/dmcs/chargeback?executionId=X&function=450&reason=4855",
                "files",      "GET  /api/dmcs/files",
                "records",    "GET  /api/dmcs/files/{id}/records"
        ));
        return ResponseEntity.ok(body);
    }

    // POST /api/dmcs/chargeback?executionId=X&function=450&reason=4855
    @PostMapping("/chargeback")
    public ResponseEntity<?> chargeback(
            @RequestParam(required = false) Long executionId,
            @RequestParam(defaultValue = "450") String function,
            @RequestParam(defaultValue = "4855") String reason,
            @RequestParam(defaultValue = "100") int limit) {
        try {
            IssIpmFile file = chargebackService.generateChargeback(executionId, function, reason, limit);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("fileId",         file.getId());
            body.put("fileName",       file.getFileName());
            body.put("ipmFileId",      file.getFileId());
            body.put("direction",      file.getDirection());
            body.put("nbTransactions", file.getNbTransactions());
            body.put("totalAmount",    file.getTotalAmount());
            body.put("status",         file.getStatus());
            body.put("asciiPath",      file.getFilePathAscii());
            body.put("binaryPath",     file.getFilePathBinary());
            return ResponseEntity.ok(body);
        } catch (Exception e) {
            log.error("[API] Chargeback error: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", e.getMessage()));
        }
    }

    // GET /api/dmcs/files
    @GetMapping("/files")
    public ResponseEntity<List<IssIpmFile>> files() {
        return ResponseEntity.ok(issIpmFileRepository.findAllByOrderByGenerationDateDesc());
    }

    // GET /api/dmcs/files/{id}/records
    @GetMapping("/files/{id}/records")
    public ResponseEntity<List<IssIpmRecord>> records(@PathVariable Long id) {
        return ResponseEntity.ok(issIpmRecordRepository.findByIpmFileId(id));
    }

    // POST /api/dmcs/read?path=... (lit un fichier IPM reçu, direction=IN)
    @PostMapping("/read")
    public ResponseEntity<?> read(@RequestParam String path) {
        try {
            IssIpmFile file = readerService.readFile(path);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("fileId",         file.getId());
            body.put("fileName",       file.getFileName());
            body.put("direction",      file.getDirection());
            body.put("nbTransactions", file.getNbTransactions());
            body.put("totalAmount",    file.getTotalAmount());
            body.put("status",         file.getStatus());
            return ResponseEntity.ok(body);
        } catch (Exception e) {
            log.error("[API] Read error: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", e.getMessage()));
        }
    }
}
