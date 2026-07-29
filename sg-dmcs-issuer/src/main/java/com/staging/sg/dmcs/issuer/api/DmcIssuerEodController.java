package com.staging.sg.dmcs.issuer.api;

import com.staging.sg.dmcs.issuer.service.DmcIssuerEodService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/dmcs/eod")
public class DmcIssuerEodController {
    private final DmcIssuerEodService service;

    public DmcIssuerEodController(DmcIssuerEodService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<DmcIssuerEodService.EodResult> run(
            @RequestParam(required = false) LocalDate businessDate) {
        return ResponseEntity.ok(service.run(
                businessDate == null ? LocalDate.now() : businessDate));
    }
}
