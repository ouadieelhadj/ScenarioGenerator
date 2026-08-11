package com.staging.sg.onboarding.integration;

import com.staging.sg.onboarding.port.Way4ConnectorTransportException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HttpWay4ConnectorAdapterTest {

    @Test
    void disabledConnectorIsRetryableAndNeverBecomesAFunctionalReject() {
        HttpWay4ConnectorAdapter adapter = new HttpWay4ConnectorAdapter(false,
                "http://127.0.0.1:8580", "", "", "", "way4.generate");

        assertThatThrownBy(() -> adapter.generate(null, "correlation"))
                .isInstanceOfSatisfying(Way4ConnectorTransportException.class,
                        error -> org.assertj.core.api.Assertions.assertThat(error.retryable()).isTrue());
    }
}
