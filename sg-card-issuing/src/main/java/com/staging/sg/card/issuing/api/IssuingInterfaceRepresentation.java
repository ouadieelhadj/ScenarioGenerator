package com.staging.sg.card.issuing.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.staging.sg.card.issuing.domain.IssuingInterfaceDirection;
import com.staging.sg.card.issuing.domain.IssuingInterfaceEndpoint;
import com.staging.sg.card.issuing.domain.IssuingInterfaceProtocol;
import com.staging.sg.card.issuing.domain.IssuingInterfaceStatus;
import com.staging.sg.card.issuing.domain.IssuingInterfaceType;

import java.util.Map;
import java.util.UUID;

public record IssuingInterfaceRepresentation(
        UUID id,
        String issuerId,
        IssuingInterfaceType interfaceType,
        int interfaceVersion,
        IssuingInterfaceDirection direction,
        IssuingInterfaceProtocol protocol,
        String host,
        int port,
        String basePath,
        int connectTimeoutMs,
        int readTimeoutMs,
        String tlsProfile,
        boolean secretReferenceConfigured,
        Map<String, String> parameters,
        IssuingInterfaceStatus status,
        boolean replayed) {

    public static IssuingInterfaceRepresentation from(
            IssuingInterfaceEndpoint value, ObjectMapper mapper, boolean replayed) {
        try {
            Map<String, String> parameters = mapper.readValue(
                    value.parametersJson(), new TypeReference<>() { });
            return new IssuingInterfaceRepresentation(
                    value.id(), value.issuerId(), value.interfaceType(),
                    value.interfaceVersion(), value.direction(), value.protocol(),
                    value.host(), value.port(), value.basePath(),
                    value.connectTimeoutMs(), value.readTimeoutMs(),
                    value.tlsProfile(),
                    value.secretReference() != null
                            && !value.secretReference().isBlank(),
                    parameters, value.status(), replayed);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Invalid stored interface parameters", e);
        }
    }
}
