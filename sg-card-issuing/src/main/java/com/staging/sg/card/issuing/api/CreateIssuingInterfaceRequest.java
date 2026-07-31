package com.staging.sg.card.issuing.api;

import com.staging.sg.card.issuing.domain.IssuingInterfaceDirection;
import com.staging.sg.card.issuing.domain.IssuingInterfaceProtocol;
import com.staging.sg.card.issuing.domain.IssuingInterfaceType;

import java.util.Map;

public record CreateIssuingInterfaceRequest(
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
        String secretReference,
        Map<String, String> parameters) {
    public CreateIssuingInterfaceRequest {
        parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
    }

    @Override
    public String toString() {
        return "CreateIssuingInterfaceRequest[issuerId=" + issuerId
                + ", interfaceType=" + interfaceType
                + ", interfaceVersion=" + interfaceVersion
                + ", direction=" + direction
                + ", protocol=" + protocol
                + ", host=" + host
                + ", port=" + port
                + ", secretReference=REDACTED]";
    }
}
