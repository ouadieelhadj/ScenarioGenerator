package com.staging.sg.onboarding.domain;

public enum ProvisioningDestination {
    FUTURPAYMENT,
    WAY4,
    BOTH;

    public boolean includesFuturPayment() {
        return this == FUTURPAYMENT || this == BOTH;
    }

    public boolean includesWay4() {
        return this == WAY4 || this == BOTH;
    }
}
