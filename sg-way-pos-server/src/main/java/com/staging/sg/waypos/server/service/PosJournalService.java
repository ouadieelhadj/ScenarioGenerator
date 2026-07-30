package com.staging.sg.waypos.server.service;

import com.staging.sg.common.routing.RoutingTransactionRequest;
import com.staging.sg.common.routing.RoutingTransactionResponse;
import com.staging.sg.waypos.server.domain.PosAuthorization;
import com.staging.sg.waypos.server.repository.PosAuthorizationRepository;
import com.staging.sg.waypos.server.repository.PosTerminalProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class PosJournalService {
    private final PosAuthorizationRepository repository;
    private final PanProtectionService panProtection;
    private final PosTerminalProfileRepository terminals;

    public PosJournalService(
            PosAuthorizationRepository repository, PanProtectionService panProtection,
            PosTerminalProfileRepository terminals) {
        this.repository = repository;
        this.panProtection = panProtection;
        this.terminals = terminals;
    }

    @Transactional(readOnly = true)
    public Optional<RoutingTransactionResponse> existing(String idempotencyKey) {
        return repository.findByIdempotencyKey(idempotencyKey)
                .filter(value -> !"RECEIVED".equals(value.getStatus()))
                .map(value -> new RoutingTransactionResponse(
                        value.getTransactionId(), value.getStatus(), value.getResponseCode(),
                        value.getResponseCode(), value.getAuthorizationCode(),
                        value.getRouteCode(),
                        value.getAmountMinor() == null ? null : "%012d".formatted(value.getAmountMinor()),
                        value.getArpcHex(), false,
                        java.util.Map.of("idempotentReplay", "true")));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void received(RoutingTransactionRequest request) {
        if (repository.findByIdempotencyKey(request.idempotencyKey()).isPresent()) {
            return;
        }
        String batchId = terminals.findById(request.terminalId())
                .map(com.staging.sg.waypos.server.domain.PosTerminalProfile::getBatchId)
                .orElse(null);
        repository.save(PosAuthorization.received(
                request.transactionId(), request.idempotencyKey(), request.sourceMti(),
                request.processingCode(), panProtection.mask(request.pan()),
                panProtection.hash(request.pan()), parseAmount(request.amount()),
                request.currency(), request.stan(), request.rrn(), request.terminalId(),
                request.merchantId(), batchId,
                attribute(request, "networkId"), attribute(request, "operationName"),
                request.originalTransactionId()));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(RoutingTransactionResponse response) {
        repository.findById(response.transactionId()).ifPresent(value -> {
            value.complete(response.route(), response.status(),
                    response.posResponseCode(), response.authorizationCode(),
                    response.arpcHex());
            repository.save(value);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void applyLinkedOutcome(
            RoutingTransactionRequest request,
            RoutingTransactionResponse response) {
        if (!"APPROVED".equals(response.status())
                || !java.util.Set.of("00", "10")
                .contains(response.posResponseCode())) {
            return;
        }
        String operation = attribute(request, "operationName");
        boolean reversal = "REVERSAL".equals(request.operation());
        boolean tipCompletion = "TIP_PURCHASE_COMPLETION".equals(operation);
        if (!reversal && !tipCompletion) return;
        Optional<PosAuthorization> original =
                request.originalTransactionId() != null
                ? repository.findById(request.originalTransactionId())
                : repository
                .findFirstByRrnAndTransactionIdNotOrderByCreatedAtDesc(
                        request.rrn(), request.transactionId());
        original.ifPresent(value -> {
            if (reversal) {
                if ("402".equals(attribute(request, "networkId"))) {
                    value.markAutomaticallyReversed();
                } else {
                    value.markReversed();
                }
            } else {
                value.adjustAmount(parseAmount(request.amount()));
            }
            repository.save(value);
        });
    }

    private static long parseAmount(String value) {
        if (value == null || !value.matches("\\d{1,12}")) {
            throw new IllegalArgumentException("Invalid amount");
        }
        return Long.parseLong(value);
    }

    private static String attribute(RoutingTransactionRequest request, String name) {
        return request.attributes() == null ? null : request.attributes().get(name);
    }
}
