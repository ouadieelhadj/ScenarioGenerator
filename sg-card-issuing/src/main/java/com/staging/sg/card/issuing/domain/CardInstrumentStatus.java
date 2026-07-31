package com.staging.sg.card.issuing.domain;

public enum CardInstrumentStatus {
    REQUESTED,
    PAN_RESERVED,
    INACTIVE,
    ACTIVE,
    TEMPORARILY_BLOCKED,
    LOST,
    STOLEN,
    COMPROMISED,
    EXPIRED,
    REPLACED,
    CLOSED
}
