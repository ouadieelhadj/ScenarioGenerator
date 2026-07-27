package com.staging.sg.swam.lis.switching.api;

import com.staging.sg.swam.lis.common.model.EodBatchResult;
import com.staging.sg.swam.lis.switching.service.SwitchEodService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/clearing/eod")
public class SwitchEodController {
    private final SwitchEodService service;

    public SwitchEodController(SwitchEodService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public EodBatchResult execute(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate businessDate,
            @RequestHeader(value = "X-Operator", defaultValue = "SYSTEM") String operator) {
        return service.execute(businessDate, operator);
    }
}
