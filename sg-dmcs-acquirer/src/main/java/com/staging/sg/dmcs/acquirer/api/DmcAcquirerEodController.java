package com.staging.sg.dmcs.acquirer.api;

import com.staging.sg.dmcs.acquirer.service.DmcAcquirerEodService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/dmcs/eod")
public class DmcAcquirerEodController {
    private final DmcAcquirerEodService service;

    public DmcAcquirerEodController(DmcAcquirerEodService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<DmcAcquirerEodService.EodResult> run(
            @RequestParam(required = false) LocalDate businessDate) {
        return ResponseEntity.ok(service.run(
                businessDate == null ? LocalDate.now() : businessDate));
    }
}
