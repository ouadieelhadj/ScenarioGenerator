package com.staging.sg.waypos.server.service;

import com.staging.sg.common.routing.RoutingTransactionRequest;
import com.staging.sg.common.routing.RoutingTransactionResponse;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import com.staging.sg.waypos.server.repository.PosTerminalProfileRepository;

@Service
public class PosRoutingService {
    private final PosRouteResolver routes;
    private final Internal00000Service internal;
    private final NetworkRoutingConnector network;
    private final PosJournalService journal;
    private final WayPosPinTranslationService pinTranslation;
    private final PosTerminalProfileRepository terminals;
    private final PosRecoveryService recovery;
    private Issuing00000Connector issuing;

    public PosRoutingService(
            PosRouteResolver routes, Internal00000Service internal,
            NetworkRoutingConnector network, PosJournalService journal,
            WayPosPinTranslationService pinTranslation,
            PosTerminalProfileRepository terminals,
            PosRecoveryService recovery) {
        this.routes = routes;
        this.internal = internal;
        this.network = network;
        this.journal = journal;
        this.pinTranslation = pinTranslation;
        this.terminals = terminals;
        this.recovery = recovery;
    }

    @Autowired
    void setIssuingConnector(Issuing00000Connector issuing) {
        this.issuing = issuing;
    }

    public RoutingTransactionResponse process(RoutingTransactionRequest request) {
        validate(request);
        var existing = journal.existing(request.idempotencyKey());
        if (existing.isPresent()) {
            return existing.get();
        }
        if (requiresOpenBatch(request.operation())) {
            boolean batchOpen = terminals.findById(request.terminalId())
                    .map(com.staging.sg.waypos.server.domain.PosTerminalProfile
                            ::acceptsFinancialTransactions)
                    .orElse(false);
            if (!batchOpen) {
                return RoutingTransactionResponse.decline(
                        request.transactionId(), "95", null);
            }
        }
        journal.received(request);
        String route = routes.resolve(request.pan());
        RoutingTransactionResponse response;
        if (route == null) {
            response = RoutingTransactionResponse.decline(
                    request.transactionId(), "92", null);
        } else if ("00000".equals(route)) {
            response = issuing == null
                    ? internal.process(request)
                    : issuing.process(request);
        } else {
            try {
                response = network.send(route, pinTranslation.translate(route, request));
            } catch (IllegalStateException e) {
                response = RoutingTransactionResponse.decline(
                        request.transactionId(), "96", route);
            }
        }
        journal.complete(response);
        journal.applyLinkedOutcome(request, response);
        if (route != null && !"00000".equals(route)) {
            recovery.scheduleIfNeeded(request, response, route);
        }
        return response;
    }

    private static void validate(RoutingTransactionRequest request) {
        if (request == null || request.transactionId() == null
                || request.idempotencyKey() == null || request.sourceMti() == null
                || request.pan() == null || request.amount() == null) {
            throw new IllegalArgumentException("Missing routing transaction fields");
        }
    }

    private static boolean requiresOpenBatch(String effect) {
        return java.util.Set.of("HOLD", "DEBIT", "CREDIT", "CAPTURE")
                .contains(effect);
    }
}
