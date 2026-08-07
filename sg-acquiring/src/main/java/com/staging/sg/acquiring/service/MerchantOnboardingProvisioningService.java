package com.staging.sg.acquiring.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.staging.sg.acquiring.api.AcquiringOnboardingController.OnboardingProvisioningRequest;
import com.staging.sg.acquiring.api.AcquiringOnboardingController.OnboardingProvisioningResult;
import com.staging.sg.acquiring.api.AcquiringOnboardingController.TerminalResult;
import com.staging.sg.acquiring.domain.*;
import com.staging.sg.acquiring.repository.OnboardingProvisioningReceiptRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
public class MerchantOnboardingProvisioningService {
    private final AcquiringAdministrationService administration;
    private final AcquiringIdentifierAllocator identifiers;
    private final OnboardingProvisioningReceiptRepository receipts;
    private final ObjectMapper objectMapper;

    public MerchantOnboardingProvisioningService(AcquiringAdministrationService administration,
            AcquiringIdentifierAllocator identifiers,
            OnboardingProvisioningReceiptRepository receipts, ObjectMapper objectMapper) {
        this.administration = administration;
        this.identifiers = identifiers;
        this.receipts = receipts;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public OnboardingProvisioningResult provision(OnboardingProvisioningRequest request,
            String idempotencyKey, String correlationId) {
        validate(request, idempotencyKey, correlationId);
        String fingerprint = fingerprint(request);
        var existing = receipts.findById(idempotencyKey);
        if (existing.isPresent()) {
            if (!existing.get().matches(fingerprint)) {
                throw new IllegalStateException("Idempotency key already used with another onboarding payload");
            }
            return readResult(existing.get().resultJson());
        }

        AcceptanceChannel channel = AcceptanceChannel.valueOf(request.acceptanceChannel());
        String mid = identifiers.nextMid();
        Merchant merchant = administration.createMerchant(request.acquirerId(), request.legalName(),
                request.tradingName(), request.registrationNumber(), request.country(), request.mcc(),
                request.maker(), idempotencyKey + ":merchant", correlationId);
        administration.submitMerchant(merchant.id());
        administration.approveMerchant(merchant.id(), request.checker(), correlationId);

        MerchantOutlet outlet = administration.createOutlet(merchant.id(), request.outlet().code(),
                request.outlet().name(), request.outlet().address(), request.country(), correlationId);
        AcquiringContract merchantContract = administration.createMerchantContract(request.acquirerId(),
                request.onboardingReference() + ":MERCHANT", merchant.id(),
                request.settlementAccountReference(), request.productId(), mid, request.mcc(),
                request.settlementCurrency(), channel, request.maker(),
                idempotencyKey + ":merchant-contract", correlationId);
        administration.submitContract(merchantContract.id(), request.acquirerId());
        administration.approveContract(merchantContract.id(), request.acquirerId(),
                request.checker(), correlationId);

        List<TerminalResult> terminals = new ArrayList<>();
        for (int index = 1; index <= request.outlet().terminalCount(); index++) {
            String tid = identifiers.nextTid();
            AcquiringContract deviceContract = administration.createDeviceContract(request.acquirerId(),
                    request.onboardingReference() + ":TPE:" + index, merchant.id(), merchantContract.id(),
                    request.productId(), outlet.id(), tid, channel, true, "HEX", true,
                    request.maker(), idempotencyKey + ":device-contract:" + index, correlationId);
            administration.submitContract(deviceContract.id(), request.acquirerId());
            administration.approveContract(deviceContract.id(), request.acquirerId(),
                    request.checker(), correlationId);
            terminals.add(new TerminalResult(null, tid));
        }

        OnboardingProvisioningResult result = new OnboardingProvisioningResult(merchant.id(), mid,
                List.copyOf(terminals));
        receipts.save(OnboardingProvisioningReceipt.completed(idempotencyKey, fingerprint, json(result)));
        return result;
    }

    private void validate(OnboardingProvisioningRequest request, String key, String correlationId) {
        if (request == null || request.onboardingCaseId() == null || blank(request.onboardingReference())
                || blank(request.acquirerId()) || blank(request.legalName()) || blank(request.tradingName())
                || blank(request.registrationNumber()) || request.country() == null
                || !request.country().matches("[A-Z]{2}") || request.mcc() == null
                || !request.mcc().matches("\\d{4}") || blank(request.settlementAccountReference())
                || request.settlementCurrency() == null || !request.settlementCurrency().matches("\\d{3}")
                || request.productId() == null || request.acceptanceChannel() == null
                || request.outlet() == null || blank(request.outlet().code())
                || blank(request.outlet().name()) || blank(request.outlet().address())
                || request.outlet().terminalCount() < 0 || request.outlet().terminalCount() > 999
                || blank(request.maker()) || blank(request.checker()) || request.maker().equals(request.checker())
                || blank(key) || key.length() > 128 || blank(correlationId)) {
            throw new IllegalArgumentException("Invalid merchant onboarding provisioning request");
        }
        AcceptanceChannel channel;
        try { channel = AcceptanceChannel.valueOf(request.acceptanceChannel()); }
        catch (IllegalArgumentException exception) { throw new IllegalArgumentException("Invalid acceptance channel"); }
        if (request.outlet().terminalCount() > 0 && !channel.supportsTpe()) {
            throw new IllegalArgumentException("Terminals require a TPE-enabled channel");
        }
    }

    private String fingerprint(Object value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(json(value).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
    private String json(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("Cannot serialize onboarding payload", exception); }
    }
    private OnboardingProvisioningResult readResult(String json) {
        try { return objectMapper.readValue(json, OnboardingProvisioningResult.class); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("Invalid stored onboarding result", exception); }
    }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
}
