package com.staging.sg.card.issuing.service;

import com.staging.sg.card.issuing.domain.IssuingInterfaceEndpoint;
import com.staging.sg.card.issuing.domain.IssuingInterfaceStatus;
import com.staging.sg.card.issuing.domain.IssuingInterfaceType;
import com.staging.sg.card.issuing.repository.IssuingInterfaceEndpointRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IssuingEndpointResolver {
    private final IssuingInterfaceEndpointRepository endpoints;

    public IssuingEndpointResolver(IssuingInterfaceEndpointRepository endpoints) {
        this.endpoints = endpoints;
    }

    @Transactional(readOnly = true)
    public IssuingInterfaceEndpoint requireActive(
            String issuerId, IssuingInterfaceType interfaceType) {
        return endpoints
                .findFirstByIssuerIdAndInterfaceTypeAndStatusOrderByInterfaceVersionDesc(
                        issuerId, interfaceType, IssuingInterfaceStatus.ACTIVE)
                .orElseThrow(() -> new IllegalStateException(
                        "No active database configuration for " + interfaceType));
    }
}
