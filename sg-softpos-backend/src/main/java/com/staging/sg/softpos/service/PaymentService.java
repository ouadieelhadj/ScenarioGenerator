package com.staging.sg.softpos.service;

import com.staging.sg.softpos.contracts.SoftPosContracts.*;
import com.staging.sg.softpos.domain.*;
import com.staging.sg.softpos.repository.SoftPosRepositories.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.Instant;
import java.util.*;

@Service
public class PaymentService {
    private final DeviceRepository devices; private final TransactionRepository transactions;
    private final RouteRepository routes; private final Map<PosServerMode, PosServerConnector> connectors;
    public PaymentService(DeviceRepository devices, TransactionRepository transactions, RouteRepository routes, List<PosServerConnector> connectors) {
        this.devices = devices; this.transactions = transactions; this.routes = routes;
        this.connectors = new EnumMap<>(PosServerMode.class); connectors.forEach(c -> this.connectors.put(c.mode(), c));
    }

    @Transactional(noRollbackFor = Exception.class)
    public PaymentResponse pay(String memberId, String deviceId, String environment, PaymentRequest request) {
        Optional<SoftPosTransaction> replay = transactions.findByMemberIdAndIdempotencyKey(memberId, request.idempotencyKey());
        if (replay.isPresent()) return response(replay.get(), true);
        SoftPosDevice device = devices.findByDeviceIdAndMemberId(deviceId, memberId).orElseThrow(() -> new IllegalArgumentException("Device not found"));
        if (!device.mayTransact(Instant.now())) throw new IllegalStateException("Device is not active or attested");
        SoftPosPosServerRoute route = routes.findByMemberIdAndEnvironmentAndActiveTrue(memberId, environment)
                .orElseThrow(() -> new IllegalStateException("No active POServer route"));
        SoftPosTransaction tx = SoftPosTransaction.received(memberId, deviceId, request, SoftPosHashing.sha256(request.sdkCredentialReference()));
        tx.processing(tx.getTransactionId()); transactions.saveAndFlush(tx);
        try {
            PosServerConnector connector = Optional.ofNullable(connectors.get(route.getPrimaryMode())).orElseThrow();
            var result = connector.exchange(new PosServerPaymentCommand(memberId, tx.getTransactionId(), device.getTerminalId(),
                    device.getMerchantId(), request.acceptanceChannel(), request.amountMinor(), request.currency(), request.sdkCredentialReference()), route);
            tx.complete(result.status(), result.responseCode(), result.authorizationCode());
        } catch (Exception unknownOutcome) {
            tx.unknown();
        }
        transactions.save(tx); return response(tx, false);
    }

    @Transactional(readOnly = true)
    public PaymentResponse status(String memberId, String clientTransactionId) {
        return response(transactions.findByMemberIdAndClientTransactionId(memberId, clientTransactionId)
                .orElseThrow(() -> new IllegalArgumentException("Payment not found")), false);
    }
    private static PaymentResponse response(SoftPosTransaction t, boolean replay) {
        return new PaymentResponse(t.getClientTransactionId(), t.getStatus(), t.getResponseCode(), t.getAuthorizationCode(),
                t.getStatus() == TransactionStatus.APPROVED ? "RCT-" + t.getTransactionId() : null, replay, t.getUpdatedAt());
    }
}
