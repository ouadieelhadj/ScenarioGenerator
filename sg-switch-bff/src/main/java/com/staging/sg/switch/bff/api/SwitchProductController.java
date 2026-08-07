package com.staging.sg.member.bff.api;

import com.staging.sg.member.contracts.SwitchProductStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.time.Instant;

@RestController
@RequestMapping("/api/switch/v1")
public class SwitchProductController {
    @GetMapping({"/health", "/product"})
    public SwitchProductStatus status() {
        return new SwitchProductStatus("1.0", "SWITCH",
                "FuturPayment Switch", "MEMBERS_ONLY", "UP", Instant.now());
    }
}
