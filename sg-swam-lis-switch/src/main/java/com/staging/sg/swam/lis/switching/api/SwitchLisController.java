package com.staging.sg.swam.lis.switching.api;

import com.staging.sg.swam.lis.common.model.LisFileResult;
import com.staging.sg.swam.lis.switching.service.SwitchLisGenerationService;
import org.jpos.iso.ISOException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.io.IOException;
import java.time.LocalDate;
import org.springframework.web.multipart.MultipartFile;
import com.staging.sg.swam.lis.common.model.LisImportResult;
import com.staging.sg.swam.lis.switching.service.SwitchLisImportService;

@RestController
@RequestMapping("/api/clearing/lis")
public class SwitchLisController {
    private final SwitchLisGenerationService service;
    private final SwitchLisImportService importService;
    public SwitchLisController(SwitchLisGenerationService service, SwitchLisImportService importService) {
        this.service = service; this.importService = importService;
    }
    @PostMapping("/outgoing")
    public ResponseEntity<LisFileResult> generate(@RequestParam LocalDate businessDate,
            @RequestParam String destinationBankCode) throws IOException, ISOException {
        return ResponseEntity.ok(service.generate(businessDate, destinationBankCode));
    }
    @PostMapping("/incoming")
    public ResponseEntity<LisImportResult> incoming(@RequestParam MultipartFile file)
            throws IOException, ISOException {
        return ResponseEntity.ok(importService.importFile(file.getOriginalFilename(),file.getBytes()));
    }
}
