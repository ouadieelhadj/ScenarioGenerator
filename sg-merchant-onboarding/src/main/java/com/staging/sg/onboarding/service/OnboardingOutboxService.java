package com.staging.sg.onboarding.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.staging.sg.onboarding.domain.MerchantOnboardingCase;
import com.staging.sg.onboarding.domain.OnboardingOutboxEvent;
import com.staging.sg.onboarding.domain.OnboardingWay4ExportState;
import com.staging.sg.onboarding.domain.ProvisioningDestination;
import com.staging.sg.onboarding.repository.OnboardingOutboxEventRepository;
import com.staging.sg.onboarding.repository.OnboardingWay4ExportStateRepository;
import com.staging.sg.onboarding.port.*;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@Service
public class OnboardingOutboxService {
    private final OnboardingOutboxEventRepository events;
    private final MerchantProvisioningV2CommandFactory commands;
    private final ObjectMapper objectMapper;
    private final OnboardingWay4ExportStateRepository way4States;

    public OnboardingOutboxService(OnboardingOutboxEventRepository events,
            MerchantProvisioningV2CommandFactory commands, ObjectMapper objectMapper,
            OnboardingWay4ExportStateRepository way4States) {
        this.events = events;
        this.commands = commands;
        this.objectMapper = objectMapper;
        this.way4States = way4States;
    }

    public void enqueueApproved(MerchantOnboardingCase dossier) {
        // V1 dossiers remain provisioned through their existing Acquiring workflow.
        // Do not fabricate a legal nature merely to make them eligible for the V2/WAY4 outboxes.
        ProvisioningDestination destination = dossier.provisioningDestination();
        if (destination == null) {
            throw new IllegalStateException("PROV-001: provisioning destination is required");
        }
        if (dossier.merchantType() == null) {
            return;
        }
        MerchantProvisioningCommandV2 acquiringCommand = commands.create(dossier);
        if (destination.includesFuturPayment()) {
            String key = "merchant-onboarding-v2:" + dossier.id();
            events.findByIdempotencyKey(key).orElseGet(() -> {
                String payload = serialize(acquiringCommand);
                return events.save(OnboardingOutboxEvent.provisioningRequested(
                        dossier.id(), payload, sha256(payload)));
            });
        }
        if (destination.includesWay4()) {
            String way4Key = "merchant-way4-v2:" + dossier.id();
            String reg = "PORTAL-" + dossier.id().toString().replace("-", "").toUpperCase();
            PortalWay4ExportCommand way4Command = new PortalWay4ExportCommand("2.0", dossier.id(), reg,
                    dossier.productId(), acquiringCommand.merchant(), acquiringCommand.settlement(),
                    acquiringCommand.outlets(), way4Key);
            events.findByIdempotencyKey(way4Key).orElseGet(() -> {
                String payload = serialize(way4Command);
                return events.save(OnboardingOutboxEvent.way4ExportRequested(
                        dossier.id(), payload, sha256(payload)));
            });
            way4States.findById(dossier.id()).orElseGet(() -> way4States.save(
                    OnboardingWay4ExportState.pending(dossier.id(), reg)));
        }
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot serialize provisioning event", exception);
        }
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
