package com.staging.sg.onboarding.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.staging.sg.onboarding.domain.MerchantOnboardingCase;
import com.staging.sg.onboarding.domain.OnboardingOutboxEvent;
import com.staging.sg.onboarding.repository.OnboardingOutboxEventRepository;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@Service
public class OnboardingOutboxService {
    private final OnboardingOutboxEventRepository events;
    private final MerchantProvisioningV2CommandFactory commands;
    private final ObjectMapper objectMapper;

    public OnboardingOutboxService(OnboardingOutboxEventRepository events,
            MerchantProvisioningV2CommandFactory commands, ObjectMapper objectMapper) {
        this.events = events;
        this.commands = commands;
        this.objectMapper = objectMapper;
    }

    public OnboardingOutboxEvent enqueueApproved(MerchantOnboardingCase dossier) {
        String key = "merchant-onboarding-v2:" + dossier.id();
        return events.findByIdempotencyKey(key).orElseGet(() -> {
            String payload = serialize(dossier);
            return events.save(OnboardingOutboxEvent.provisioningRequested(
                    dossier.id(), payload, sha256(payload)));
        });
    }

    private String serialize(MerchantOnboardingCase dossier) {
        try {
            return objectMapper.writeValueAsString(commands.create(dossier));
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
