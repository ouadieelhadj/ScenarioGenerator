package com.staging.sg.card.issuing.service;

import com.staging.sg.card.issuing.domain.PaymentIdentifier;
import com.staging.sg.card.issuing.domain.PaymentIdentifierStatus;
import com.staging.sg.card.issuing.port.PaymentIdentifierResolutionPort;
import com.staging.sg.card.issuing.port.PaymentIdentifierNotFoundException;
import com.staging.sg.card.issuing.repository.PaymentIdentifierRepository;
import com.staging.sg.common.issuing.PaymentIdentifierType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DatabasePaymentIdentifierResolver
        implements PaymentIdentifierResolutionPort {
    private final PaymentIdentifierRepository identifiers;

    public DatabasePaymentIdentifierResolver(
            PaymentIdentifierRepository identifiers) {
        this.identifiers = identifiers;
    }

    @Override
    @Transactional(readOnly = true)
    public ResolvedPaymentIdentifier resolve(
            String issuerId, PaymentIdentifierType type,
            String presentedIdentifier) {
        if (issuerId == null || issuerId.isBlank()
                || type == null || presentedIdentifier == null
                || presentedIdentifier.isBlank()) {
            throw new IllegalArgumentException("Invalid payment identifier");
        }
        PaymentIdentifier identifier = switch (type) {
            case PAN -> identifiers.findByIssuerIdAndPanClearAndStatus(
                    issuerId, presentedIdentifier,
                    PaymentIdentifierStatus.ACTIVE).orElse(null);
            case NETWORK_TOKEN, SECURE_REFERENCE ->
                    identifiers.findByIssuerIdAndVaultReferenceAndStatus(
                            issuerId, presentedIdentifier,
                            PaymentIdentifierStatus.ACTIVE).orElse(null);
            case WALLET_TOKEN -> null;
        };
        if (identifier == null) {
            throw new PaymentIdentifierNotFoundException();
        }
        return new ResolvedPaymentIdentifier(identifier.tokenValue());
    }
}
