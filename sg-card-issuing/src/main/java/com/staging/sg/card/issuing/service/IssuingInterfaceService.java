package com.staging.sg.card.issuing.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.staging.sg.card.issuing.api.CreateIssuingInterfaceRequest;
import com.staging.sg.card.issuing.api.IssuingInterfaceRepresentation;
import com.staging.sg.card.issuing.domain.IssuingInterfaceEndpoint;
import com.staging.sg.card.issuing.domain.IssuingInterfaceStatus;
import com.staging.sg.card.issuing.domain.OutboxEvent;
import com.staging.sg.card.issuing.repository.IssuingInterfaceEndpointRepository;
import com.staging.sg.card.issuing.repository.OutboxEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.TreeMap;
import java.util.UUID;

@Service
public class IssuingInterfaceService {
    private final IssuingInterfaceEndpointRepository endpoints;
    private final OutboxEventRepository outbox;
    private final ObjectMapper mapper;

    public IssuingInterfaceService(
            IssuingInterfaceEndpointRepository endpoints,
            OutboxEventRepository outbox, ObjectMapper mapper) {
        this.endpoints = endpoints;
        this.outbox = outbox;
        this.mapper = mapper;
    }

    @Transactional
    public IssuingInterfaceRepresentation create(
            CreateIssuingInterfaceRequest request, String callerId,
            String idempotencyKey, String correlationId) {
        String parametersJson = parametersJson(request);
        String fingerprint = CommandFingerprint.of(
                request.issuerId(), request.interfaceType(),
                request.interfaceVersion(), request.direction(),
                request.protocol(), request.host(), request.port(),
                request.basePath(), request.connectTimeoutMs(),
                request.readTimeoutMs(), request.tlsProfile(),
                request.secretReference(), parametersJson);
        var existing = endpoints
                .findByIssuerIdAndCreatedByAndCreationIdempotencyKey(
                        request.issuerId(), callerId, idempotencyKey);
        if (existing.isPresent()) {
            if (!existing.get().creationMatches(fingerprint)) {
                throw new IllegalStateException(
                        "Idempotency key already used with another interface payload");
            }
            return IssuingInterfaceRepresentation.from(
                    existing.get(), mapper, true);
        }
        IssuingInterfaceEndpoint endpoint = IssuingInterfaceEndpoint.draft(
                request.issuerId(), request.interfaceType(),
                request.interfaceVersion(), request.direction(),
                request.protocol(), request.host(), request.port(),
                request.basePath(), request.connectTimeoutMs(),
                request.readTimeoutMs(), request.tlsProfile(),
                request.secretReference(), parametersJson, callerId,
                idempotencyKey, fingerprint);
        endpoints.save(endpoint);
        emit(endpoint, "IssuingInterfaceCreated", correlationId);
        return IssuingInterfaceRepresentation.from(endpoint, mapper, false);
    }

    @Transactional
    public IssuingInterfaceRepresentation approve(
            UUID id, String issuerId, String approver, String correlationId) {
        IssuingInterfaceEndpoint endpoint = owned(id, issuerId);
        if (endpoint.approve(approver)) {
            endpoints.save(endpoint);
            emit(endpoint, "IssuingInterfaceApproved", correlationId);
        }
        return IssuingInterfaceRepresentation.from(endpoint, mapper, false);
    }

    @Transactional
    public IssuingInterfaceRepresentation activate(
            UUID id, String issuerId, String correlationId) {
        IssuingInterfaceEndpoint endpoint = owned(id, issuerId);
        var previous = endpoints
                .findFirstByIssuerIdAndInterfaceTypeAndStatusOrderByInterfaceVersionDesc(
                        issuerId, endpoint.interfaceType(),
                        IssuingInterfaceStatus.ACTIVE);
        if (previous.isPresent() && !previous.get().id().equals(id)) {
            previous.get().disable();
            endpoints.saveAndFlush(previous.get());
            emit(previous.get(), "IssuingInterfaceDisabled", correlationId);
        }
        if (endpoint.activate()) {
            endpoints.save(endpoint);
            emit(endpoint, "IssuingInterfaceActivated", correlationId);
        }
        return IssuingInterfaceRepresentation.from(endpoint, mapper, false);
    }

    @Transactional
    public IssuingInterfaceRepresentation disable(
            UUID id, String issuerId, String correlationId) {
        IssuingInterfaceEndpoint endpoint = owned(id, issuerId);
        if (endpoint.disable()) {
            endpoints.save(endpoint);
            emit(endpoint, "IssuingInterfaceDisabled", correlationId);
        }
        return IssuingInterfaceRepresentation.from(endpoint, mapper, false);
    }

    private IssuingInterfaceEndpoint owned(UUID id, String issuerId) {
        IssuingInterfaceEndpoint endpoint = endpoints.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown issuing interface"));
        if (!endpoint.issuerId().equals(issuerId)) {
            throw new IllegalArgumentException("Unknown issuing interface");
        }
        return endpoint;
    }

    private String parametersJson(CreateIssuingInterfaceRequest request) {
        request.parameters().keySet().forEach(key -> {
            String normalized = key.toLowerCase()
                    .replaceAll("[^a-z0-9]", "");
            if (normalized.contains("password")
                    || normalized.contains("secret")
                    || normalized.contains("token")
                    || normalized.contains("credential")
                    || normalized.contains("privatekey")
                    || normalized.contains("apikey")) {
                throw new IllegalArgumentException(
                        "Secret values are forbidden in interface parameters; "
                                + "use secretReference");
            }
        });
        try {
            return mapper.writeValueAsString(new TreeMap<>(request.parameters()));
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid interface parameters", e);
        }
    }

    private void emit(
            IssuingInterfaceEndpoint endpoint, String eventType,
            String correlationId) {
        String payload = "{\"issuerId\":\"" + safe(endpoint.issuerId())
                + "\",\"interfaceId\":\"" + endpoint.id()
                + "\",\"interfaceType\":\"" + endpoint.interfaceType()
                + "\",\"interfaceVersion\":" + endpoint.interfaceVersion()
                + ",\"status\":\"" + endpoint.status() + "\"}";
        outbox.save(OutboxEvent.pending(
                "IssuingInterface", endpoint.id().toString(), eventType,
                correlationId, payload));
    }

    private static String safe(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
