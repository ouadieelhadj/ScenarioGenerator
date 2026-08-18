package com.staging.sg.waypos.server.api;

import com.staging.sg.softpos.contracts.SoftPosContracts.*;
import com.staging.sg.common.routing.*;
import com.staging.sg.waypos.server.repository.PosTerminalProfileRepository;
import com.staging.sg.waypos.server.service.PosRoutingService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/internal/softpos/v1")
public class WayPosSoftPosInternalController {
    private final PosRoutingService routing;
    private final PosTerminalProfileRepository terminals;
    private final boolean labCredentialsEnabled;

    public WayPosSoftPosInternalController(PosRoutingService routing,
            PosTerminalProfileRepository terminals,
            @Value("${way-pos.softpos-lab-credentials-enabled:false}") boolean labCredentialsEnabled) {
        this.routing = routing; this.terminals = terminals;
        this.labCredentialsEnabled = labCredentialsEnabled;
    }

    @PostMapping("/transactions")
    public PosServerPaymentResult transact(@RequestBody PosServerPaymentCommand command) {
        var terminal = terminals.findById(command.terminalId())
                .filter(t -> "SOFTPOS".equals(t.getTerminalType()))
                .filter(t -> t.getMemberId().equals(command.memberId()))
                .orElseThrow(() -> new IllegalArgumentException("SoftPOS terminal not found"));
        if (!labCredentialsEnabled || !"LABREF:APPROVED_CARD".equals(command.sdkCredentialReference())) {
            throw new IllegalStateException("Certified SDK/HSM credential adapter is not configured");
        }
        String laboratoryPan = "4000000000000002";
        String laboratoryExpiry = "2912";
        RoutingTransactionResponse response = routing.process(new RoutingTransactionRequest(
                "1.0", command.posTransactionId(), command.posTransactionId(),
                command.posTransactionId(), "DEBIT", "0200", "000000",
                laboratoryPan, laboratoryExpiry, "%012d".formatted(command.amountMinor()),
                command.currency(), stan(command.posTransactionId()), null,
                terminal.getTerminalId(), terminal.getMerchantId(), null, null,
                null, Map.of("acceptanceChannel", command.acceptanceChannel().name(),
                        "operationName", "SOFTPOS_PURCHASE")));
        TransactionStatus status = "APPROVED".equals(response.status())
                ? TransactionStatus.APPROVED : TransactionStatus.DECLINED;
        return new PosServerPaymentResult(status, response.posResponseCode(),
                response.authorizationCode());
    }

    private static String stan(String value) {
        int positive = value.hashCode() & 0x7fffffff;
        return "%06d".formatted(positive % 1_000_000);
    }
}
