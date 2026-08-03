package com.staging.sg.threeds.member.api;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@Profile("connected-e2e")
@RequestMapping("/api/3ds/member/v1/acs/sandbox")
public class SandboxChallengeUiController {
    private final String challengeOtp;

    public SandboxChallengeUiController(
            @Value("${three-ds.sandbox.challenge-otp:}") String challengeOtp) {
        this.challengeOtp = challengeOtp;
    }

    @GetMapping("/display")
    public ResponseEntity<?> display() {
        if (challengeOtp == null || challengeOtp.isBlank()) {
            return ResponseEntity.status(503).body(Map.of(
                    "error", "OTP sandbox non configure"));
        }
        return ResponseEntity.ok(Map.of(
                "sandbox", true,
                "issuer", "LanaCash ACS",
                "deliveryChannel", "SMS_SIMULATED",
                "otp", challengeOtp,
                "maxAttempts", 3));
    }
}
