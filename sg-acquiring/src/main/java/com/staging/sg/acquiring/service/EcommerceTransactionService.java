package com.staging.sg.acquiring.service;

import com.staging.sg.acquiring.domain.*;
import com.staging.sg.acquiring.port.EcommerceNetworkCommand;
import com.staging.sg.acquiring.port.EcommerceNetworkException;
import com.staging.sg.acquiring.port.EcommerceNetworkPort;
import com.staging.sg.acquiring.repository.*;
import com.staging.sg.common.contract.PaymentContractStatus;
import com.staging.sg.common.contract.PaymentContractType;
import com.staging.sg.common.ecommerce.*;
import com.staging.sg.common.issuing.PaymentIdentifierType;
import com.staging.sg.common.routing.RoutingTransactionResponse;
import org.springframework.stereotype.Service;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;

@Service
public class EcommerceTransactionService {
    private final EcommerceAcceptanceProfileRepository profiles;
    private final EcommerceStoreRepository stores;
    private final AcquiringContractRepository contracts;
    private final AcquiringContractDetailRepository details;
    private final EcommerceTransactionRepository transactions;
    private final AcquiringOutboxEventRepository outbox;
    private final EcommerceNetworkPort network;
    private final EcommerceRouteResolver routes;

    public EcommerceTransactionService(EcommerceAcceptanceProfileRepository profiles,
            EcommerceStoreRepository stores, AcquiringContractRepository contracts,
            AcquiringContractDetailRepository details,
            EcommerceTransactionRepository transactions,
            AcquiringOutboxEventRepository outbox, EcommerceNetworkPort network,
            EcommerceRouteResolver routes) {
        this.profiles = profiles;
        this.stores = stores;
        this.contracts = contracts;
        this.details = details;
        this.transactions = transactions;
        this.outbox = outbox;
        this.network = network;
        this.routes = routes;
    }

    public EcommercePurchaseResponse purchase(EcommercePurchaseRequest request) {
        validate(request);
        EcommerceNetworkRoute resolvedRoute = routes.resolve(
                request.paymentIdentifier(), request.networkRoute());
        String fingerprint = fingerprint(request, resolvedRoute);
        var found = transactions.findByAcquirerIdAndIdempotencyKey(
                request.acquirerId(), request.idempotencyKey());
        EcommerceTransaction transaction;
        boolean replay;
        if (found.isPresent()) {
            transaction = found.get();
            if (!transaction.matches(fingerprint)) {
                throw new IllegalStateException(
                        "Idempotency key already used with another ecommerce payload");
            }
            if (!transaction.canRetry()) return transaction.response(true);
            replay = true;
        } else {
            EcommerceAcceptanceProfile profile = activeProfile(request);
            AcquiringContract contract = activeContract(profile, request.acquirerId());
            AcquiringContractDetail detail = details.findById(contract.id())
                    .orElseThrow(() -> new IllegalStateException(
                            "Missing ecommerce acquiring contract detail"));
            String stan = "%06d".formatted(Math.floorMod(
                    request.transactionId().hashCode(), 1_000_000));
            String rrn = ZonedDateTime.now(ZoneOffset.UTC)
                    .format(java.time.format.DateTimeFormatter.ofPattern("MMddHH")) + stan;
            transaction = EcommerceTransaction.received(request.acquirerId(),
                    request.transactionId(), request.correlationId(),
                    request.idempotencyKey(), fingerprint, profile.id(),
                    contract.id(), request.merchantOrderId(), request.amountMinor(),
                    request.currency(), request.paymentIdentifierType(),
                    resolvedRoute, request.authenticationStatus(), stan, rrn);
            transactions.save(transaction);
            replay = false;
            return authorize(request, resolvedRoute, profile, detail, transaction, replay);
        }
        EcommerceAcceptanceProfile profile = activeProfile(request);
        AcquiringContract contract = activeContract(profile, request.acquirerId());
        AcquiringContractDetail detail = details.findById(contract.id())
                .orElseThrow(() -> new IllegalStateException(
                        "Missing ecommerce acquiring contract detail"));
        return authorize(request, resolvedRoute, profile, detail, transaction, replay);
    }

    private EcommercePurchaseResponse authorize(EcommercePurchaseRequest request,
            EcommerceNetworkRoute resolvedRoute,
            EcommerceAcceptanceProfile profile, AcquiringContractDetail detail,
            EcommerceTransaction transaction, boolean replay) {
        try {
            RoutingTransactionResponse result = network.authorize(
                    new EcommerceNetworkCommand(request.transactionId(),
                            request.correlationId(), request.idempotencyKey(),
                            request.paymentIdentifier(), request.expiry(),
                            request.amountMinor(), request.currency(), transaction.stan(),
                            transaction.rrn(), profile.logicalTerminalId(),
                            detail.merchantAcceptorId(), resolvedRoute,
                            request.authenticationStatus()));
            transaction.decide(result);
            transactions.save(transaction);
            outbox.save(AcquiringOutboxEvent.pending("EcommerceTransaction",
                    transaction.id(), "EcommercePurchaseDecided",
                    request.correlationId(), "{\"status\":\""
                            + transaction.response(replay).status() + "\"}"));
            return transaction.response(replay);
        } catch (EcommerceNetworkException e) {
            transaction.unknown("91");
            transactions.save(transaction);
            throw e;
        }
    }

    private EcommerceAcceptanceProfile activeProfile(EcommercePurchaseRequest request) {
        EcommerceAcceptanceProfile profile = profiles.findById(request.profileId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown ecommerce acceptance profile"));
        if (!profile.isActive() || !profile.acquirerId().equals(request.acquirerId())
                || !profile.currency().equals(request.currency())) {
            throw new IllegalStateException("An active matching ecommerce profile is required");
        }
        EcommerceStore store = stores.findById(profile.storeId())
                .orElseThrow(() -> new IllegalStateException("Ecommerce store is missing"));
        if (store.status() != EcommerceStatus.ACTIVE) {
            throw new IllegalStateException("The ecommerce store must be active");
        }
        return profile;
    }

    private AcquiringContract activeContract(EcommerceAcceptanceProfile profile,
            String acquirerId) {
        AcquiringContract contract = contracts.findById(profile.contractId())
                .orElseThrow(() -> new IllegalStateException(
                        "Ecommerce acquiring contract is missing"));
        if (!contract.institutionId().equals(acquirerId)
                || contract.contractType() != PaymentContractType.ACQUIRING_MERCHANT
                || contract.status() != PaymentContractStatus.ACTIVE) {
            throw new IllegalStateException("An active ecommerce merchant contract is required");
        }
        return contract;
    }

    private static void validate(EcommercePurchaseRequest request) {
        if (request == null || !"1.0".equals(request.schemaVersion())
                || blank(request.transactionId()) || blank(request.correlationId())
                || blank(request.idempotencyKey()) || blank(request.acquirerId())
                || request.profileId() == null || blank(request.merchantOrderId())
                || request.amountMinor() <= 0 || request.amountMinor() > 999_999_999_999L
                || request.currency() == null || !request.currency().matches("\\d{3}")
                || request.paymentIdentifierType() != PaymentIdentifierType.PAN
                || request.paymentIdentifier() == null
                || !request.paymentIdentifier().matches("\\d{12,19}")
                || request.expiry() == null || !request.expiry().matches("\\d{4}")
                || request.networkRoute() == null || request.authenticationStatus() == null) {
            throw new IllegalArgumentException("Invalid ecommerce purchase request");
        }
        if (request.networkRoute() == EcommerceNetworkRoute.VISA) {
            throw new IllegalStateException("Visa ecommerce routing is not implemented");
        }
        if (request.authenticationStatus() != EcommerceAuthenticationStatus.NOT_PERFORMED
                || request.eci() != null || request.cavv() != null
                || request.directoryServerTransactionId() != null) {
            throw new IllegalStateException(
                    "3DS data is not accepted until the 3DS module is implemented");
        }
    }

    private static String fingerprint(EcommercePurchaseRequest request,
            EcommerceNetworkRoute resolvedRoute) {
        return AcquiringFingerprint.of(request.schemaVersion(), request.transactionId(),
                request.acquirerId(), request.profileId(), request.merchantOrderId(),
                request.amountMinor(), request.currency(), request.paymentIdentifierType(),
                request.paymentIdentifier(), request.expiry(), resolvedRoute,
                request.authenticationStatus());
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
