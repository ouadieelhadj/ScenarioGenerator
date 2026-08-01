package com.staging.sg.cardnetwork.gateway.service;

import com.staging.sg.common.routing.RoutingTransactionRequest;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CardNetworkGatewayServiceTest {
    @Test
    void visaFailsClosedWhileItsFinancialDownstreamIsAbsent() {
        CardNetworkGatewayService service = new CardNetworkGatewayService(
                false, "http://127.0.0.1:1", false, "http://127.0.0.1:1");
        RoutingTransactionRequest request = new RoutingTransactionRequest(
                "1.0", "TX-1", "CORR-1", "IDEM-1", "AUTHORIZATION",
                "0100", "000000", "4111111111111111", "2912",
                "000000001000", "504", "000001", "000000000001",
                "ECOM0001", "MID000000000001", null, null, null,
                Map.of("cardProgram", "VISA"));

        assertThatThrownBy(() -> service.route(request))
                .isInstanceOf(CardNetworkGatewayService.DownstreamUnavailableException.class)
                .hasMessageContaining("Visa financial downstream");
    }
}
