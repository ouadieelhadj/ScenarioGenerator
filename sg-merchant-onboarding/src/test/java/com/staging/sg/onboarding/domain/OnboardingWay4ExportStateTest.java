package com.staging.sg.onboarding.domain;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OnboardingWay4ExportStateTest {

    @Test
    void tracesRetryableAndFinalFailuresWithoutInventingIdentifiers() {
        OnboardingWay4ExportState state = OnboardingWay4ExportState.pending(
                UUID.randomUUID(), "PORTAL-TEST");

        state.failed("WAY4_TEMPORARY", "Connector unavailable", true);
        assertThat(state.status()).isEqualTo("PENDING");
        assertThat(state.lastFailureRetryable()).isTrue();
        assertThat(state.lastErrorCode()).isEqualTo("WAY4_TEMPORARY");

        state.failed("WAY4_BUSINESS_REJECT", "Business rejection", false);
        assertThat(state.status()).isEqualTo("REJECTED");
        assertThat(state.lastFailureRetryable()).isFalse();
        assertThat(state.lastErrorMessage()).isEqualTo("Business rejection");
    }
}
