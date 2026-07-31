package com.staging.sg.card.issuing.repository;

import com.staging.sg.card.issuing.domain.IssuingInterfaceEndpoint;
import com.staging.sg.card.issuing.domain.IssuingInterfaceStatus;
import com.staging.sg.card.issuing.domain.IssuingInterfaceType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface IssuingInterfaceEndpointRepository
        extends JpaRepository<IssuingInterfaceEndpoint, UUID> {
    Optional<IssuingInterfaceEndpoint>
    findByIssuerIdAndCreatedByAndCreationIdempotencyKey(
            String issuerId, String createdBy, String idempotencyKey);

    Optional<IssuingInterfaceEndpoint>
    findFirstByIssuerIdAndInterfaceTypeAndStatusOrderByInterfaceVersionDesc(
            String issuerId, IssuingInterfaceType interfaceType,
            IssuingInterfaceStatus status);
}
