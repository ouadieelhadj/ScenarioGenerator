package com.staging.sg.card.issuing.domain;

public enum CardContractStatus {
    DRAFT,
    PENDING_APPROVAL,
    ACTIVE,
    SUSPENDED,
    BLOCKED,
    CLOSING,
    CLOSED,
    REJECTED
}
