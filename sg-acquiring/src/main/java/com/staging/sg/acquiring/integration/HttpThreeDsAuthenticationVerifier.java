package com.staging.sg.acquiring.integration;

import com.staging.sg.acquiring.port.EcommerceAuthenticationVerificationPort;
import com.staging.sg.common.ecommerce.EcommercePurchaseRequest;
import com.staging.sg.common.threeds.ThreeDsProgram;
import com.staging.sg.common.threeds.ThreeDsVerificationRequest;
import com.staging.sg.common.threeds.ThreeDsVerificationResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.UUID;

@Component
public class HttpThreeDsAuthenticationVerifier
        implements EcommerceAuthenticationVerificationPort {
    private final boolean enabled;
    private final RestClient member;

    public HttpThreeDsAuthenticationVerifier(
            @Value("${acquiring.three-ds.enabled:false}") boolean enabled,
            @Value("${acquiring.three-ds.base-url:http://127.0.0.1:8560}") String baseUrl) {
        this.enabled = enabled;
        this.member = RestClient.builder().baseUrl(baseUrl).build();
    }

    @Override
    public boolean verifyAndConsume(EcommercePurchaseRequest request) {
        if (!enabled) return false;
        ThreeDsVerificationRequest verification = new ThreeDsVerificationRequest(
                "1.0", request.transactionId(),
                UUID.fromString(request.directoryServerTransactionId()),
                program(request.eci()), request.eci(), request.cavv(),
                request.merchantOrderId(), request.amountMinor(), request.currency());
        ThreeDsVerificationResponse response = member.post()
                .uri("/api/3ds/member/v1/verifications")
                .body(verification).retrieve().body(ThreeDsVerificationResponse.class);
        return response != null && response.valid() && response.sandboxEvidence();
    }

    private static ThreeDsProgram program(String eci) {
        return eci != null && eci.startsWith("0") && (eci.endsWith("5") || eci.endsWith("6"))
                ? ThreeDsProgram.VISA : ThreeDsProgram.MASTERCARD;
    }
}
