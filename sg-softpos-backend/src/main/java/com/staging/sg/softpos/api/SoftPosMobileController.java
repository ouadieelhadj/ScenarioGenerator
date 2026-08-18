package com.staging.sg.softpos.api;

import com.staging.sg.softpos.contracts.SoftPosContracts.*;
import com.staging.sg.softpos.service.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.time.Duration;
import java.util.Map;

@RestController
@RequestMapping("/api/softpos/v1")
public class SoftPosMobileController {
    private final ActivationService activations; private final PaymentService payments; private final RequestIdentity identities;
    public SoftPosMobileController(ActivationService activations, PaymentService payments, RequestIdentity identities) {
        this.activations = activations; this.payments = payments; this.identities = identities;
    }
    @PostMapping("/activations/consume") public ActivationResponse activate(@RequestBody ActivationConsumeRequest request) { return activations.consume(request); }
    @PostMapping("/integrity/verdicts") public ResponseEntity<?> integrity(Authentication auth,
            @RequestHeader(value = "X-Member-Id", required = false) String member,
            @RequestBody IntegrityVerdictRequest request) {
        var id = identities.resolve(auth, member, request.deviceId()); activations.attest(id.memberId(), request, Duration.ofMinutes(15));
        return ResponseEntity.ok(Map.of("status", "ACCEPTED"));
    }
    @PostMapping("/payments") public PaymentResponse pay(Authentication auth,
            @RequestHeader(value = "X-Member-Id", required = false) String member,
            @RequestHeader(value = "X-Device-Id", required = false) String device,
            @RequestHeader(value = "X-Environment", defaultValue = "LAB") String environment,
            @RequestBody PaymentRequest request) {
        var id = identities.resolve(auth, member, device); return payments.pay(id.memberId(), required(id.deviceId()), environment, request);
    }
    @GetMapping("/payments/{clientTransactionId}") public PaymentResponse status(Authentication auth,
            @RequestHeader(value = "X-Member-Id", required = false) String member,
            @RequestHeader(value = "X-Device-Id", required = false) String device,
            @PathVariable String clientTransactionId) {
        var id = identities.resolve(auth, member, device); return payments.status(id.memberId(), clientTransactionId);
    }
    @PostMapping("/qr/merchant-presented") public PaymentResponse merchantQr(Authentication auth,
            @RequestHeader(value = "X-Member-Id", required = false) String member,
            @RequestHeader(value = "X-Device-Id", required = false) String device,
            @RequestHeader(value = "X-Environment", defaultValue = "LAB") String environment,
            @RequestBody QrRequest request) {
        if (request.acceptanceChannel() != AcceptanceChannel.QR_MPM) throw new IllegalArgumentException("QR_MPM required");
        return pay(auth, member, device, environment, asPayment(request));
    }
    @PostMapping("/qr/consumer-presented") public PaymentResponse consumerQr(Authentication auth,
            @RequestHeader(value = "X-Member-Id", required = false) String member,
            @RequestHeader(value = "X-Device-Id", required = false) String device,
            @RequestHeader(value = "X-Environment", defaultValue = "LAB") String environment,
            @RequestBody QrRequest request) {
        if (request.acceptanceChannel() != AcceptanceChannel.QR_CPM) throw new IllegalArgumentException("QR_CPM required");
        return pay(auth, member, device, environment, asPayment(request));
    }
    private static PaymentRequest asPayment(QrRequest q) { return new PaymentRequest(q.clientTransactionId(), q.idempotencyKey(), q.acceptanceChannel(), q.amountMinor(), q.currency(), q.qrReference(), "QR", Map.of("expiresAt", String.valueOf(q.expiresAt()))); }
    private static String required(String value) { if (value == null || value.isBlank()) throw new IllegalArgumentException("device_id is required"); return value; }
}
