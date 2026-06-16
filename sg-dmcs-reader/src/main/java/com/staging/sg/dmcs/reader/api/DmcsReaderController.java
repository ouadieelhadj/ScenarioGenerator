package com.staging.sg.dmcs.reader.api;

import com.staging.sg.common.entity.IpmFile;
import com.staging.sg.common.entity.IpmRecord;
import com.staging.sg.dmcs.reader.service.IpmReaderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api/dmcs")
public class DmcsReaderController {

    private static final Logger log =
            LoggerFactory.getLogger(DmcsReaderController.class);

    private final IpmReaderService readerService;

    public DmcsReaderController(IpmReaderService readerService) {
        this.readerService = readerService;
    }

    // GET /api/dmcs/status
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("application", "SG DMCS Reader");
        body.put("version",     "1.0.0-SNAPSHOT");
        body.put("port",        8300);
        body.put("endpoints", Map.of(
                "files",          "GET /api/dmcs/files",
                "file",           "GET /api/dmcs/files/{id}",
                "records",        "GET /api/dmcs/files/{id}/records",
                "presentments",   "GET /api/dmcs/files/{id}/presentments",
                "stats",          "GET /api/dmcs/files/{id}/stats",
                "readBinary",     "GET /api/dmcs/files/{id}/read/binary",
                "readAscii",      "GET /api/dmcs/files/{id}/read/ascii",
                "directory",      "GET /api/dmcs/directory"
        ));
        return ResponseEntity.ok(body);
    }

    // GET /api/dmcs/files
    @GetMapping("/files")
    public ResponseEntity<List<IpmFile>> listFiles(
            @RequestParam(required = false) String date) {
        if (date != null) {
            return ResponseEntity.ok(
                    readerService.listFilesByDate(LocalDate.parse(date)));
        }
        return ResponseEntity.ok(readerService.listFiles());
    }

    // GET /api/dmcs/files/{id}
    @GetMapping("/files/{id}")
    public ResponseEntity<?> getFile(@PathVariable Long id) {
        IpmFile file = readerService.getFile(id);
        if (file == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(file);
    }

    // GET /api/dmcs/files/{id}/records
    @GetMapping("/files/{id}/records")
    public ResponseEntity<List<IpmRecord>> getRecords(@PathVariable Long id) {
        return ResponseEntity.ok(readerService.getRecords(id));
    }

    // GET /api/dmcs/files/{id}/presentments
    @GetMapping("/files/{id}/presentments")
    public ResponseEntity<List<IpmRecord>> getPresentments(@PathVariable Long id) {
        return ResponseEntity.ok(readerService.getPresentments(id));
    }

    // GET /api/dmcs/files/{id}/stats
    @GetMapping("/files/{id}/stats")
    public ResponseEntity<Map<String, Object>> getStats(@PathVariable Long id) {
        return ResponseEntity.ok(readerService.getStats(id));
    }

    // GET /api/dmcs/files/{id}/read/binary
    @GetMapping("/files/{id}/read/binary")
    public ResponseEntity<List<Map<String, Object>>> readBinary(
            @PathVariable Long id) {
        return ResponseEntity.ok(readerService.readBinaryFile(id));
    }

    // GET /api/dmcs/files/{id}/read/ascii
    @GetMapping("/files/{id}/read/ascii")
    public ResponseEntity<List<Map<String, Object>>> readAscii(
            @PathVariable Long id) {
        return ResponseEntity.ok(readerService.readAsciiFile(id));
    }

    // GET /api/dmcs/directory
    @GetMapping("/directory")
    public ResponseEntity<List<Map<String, Object>>> listDirectory() {
        return ResponseEntity.ok(readerService.listDmcsDirectory());
    }
}
