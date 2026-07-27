package com.staging.sg.swam.lis.member.api;

import com.staging.sg.swam.lis.common.model.EodBatchResult;
import com.staging.sg.swam.lis.member.service.MemberEodService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/clearing/eod")
public class MemberEodController {
    private final MemberEodService service;

    public MemberEodController(MemberEodService service) {
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
