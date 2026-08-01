package com.staging.sg.ecommerce.simulator.service;

import com.staging.sg.common.ecommerce.EcommerceNetworkRoute;
import com.staging.sg.ecommerce.simulator.api.SimulatorPurchaseRequest;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class EcommerceSimulatorClientTest {
    @Test
    void rejectsVisaUntilItsRouteIsImplemented() {
        EcommerceSimulatorClient client = new EcommerceSimulatorClient(
                "http://127.0.0.1:1", 100, 100);
        SimulatorPurchaseRequest request = new SimulatorPurchaseRequest(
                null, null, null, "ACQTEST", UUID.randomUUID(), "ORDER-1",
                1000, "504", "5321962145453348", "2912",
                EcommerceNetworkRoute.VISA);

        assertThatThrownBy(() -> client.purchase(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Visa");
        assertThat(request.toString()).contains("sensitiveData=REDACTED")
                .doesNotContain("5321962145453348");
    }
}
