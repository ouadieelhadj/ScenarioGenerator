package com.staging.sg.ecommerce.simulator.service;

import com.staging.sg.common.ecommerce.EcommerceNetworkRoute;
import com.staging.sg.ecommerce.simulator.api.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MerchantStorefrontServiceTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void createsTheOrderFromTheServerCatalogPrice() {
        MerchantStorefrontService service = service(mock(EcommerceSimulatorClient.class));

        MerchantOrderResponse order = service.createOrder(new MerchantOrderCreateRequest(
                List.of(new MerchantOrderItemRequest("SG-LAB", 2))));

        assertThat(order.lines()).hasSize(1);
        assertThat(order.totalMinor()).isEqualTo(2000);
        assertThat(order.currency()).isEqualTo("504");
        assertThat(order.status()).isEqualTo("AWAITING_PAYMENT");
    }

    @Test
    void startsAChallengeWithAutomaticFinancialRouting() throws Exception {
        EcommerceSimulatorClient client = mock(EcommerceSimulatorClient.class);
        UUID checkoutId = UUID.randomUUID();
        when(client.startAutomatic(org.mockito.ArgumentMatchers.any()))
                .thenReturn(InteractiveCheckoutStartResponse.challenge(
                        checkoutId, "http://acs.test/challenge"));
        Path profile = temporaryDirectory.resolve("profile-id");
        Files.writeString(profile, UUID.randomUUID().toString());
        MerchantStorefrontService service = new MerchantStorefrontService(
                client, profile.toString(), MerchantSiteType.NATIONAL);
        MerchantOrderResponse order = service.createOrder(new MerchantOrderCreateRequest(
                List.of(new MerchantOrderItemRequest("SG-LAB", 1))));

        MerchantPaymentStartResponse started = service.startPayment(order.orderId(),
                new MerchantCardPaymentRequest("CLIENT TEST", "5321962145453348", "2912"));

        ArgumentCaptor<SimulatorPurchaseRequest> request =
                ArgumentCaptor.forClass(SimulatorPurchaseRequest.class);
        verify(client).startAutomatic(request.capture());
        assertThat(request.getValue().networkRoute()).isEqualTo(EcommerceNetworkRoute.AUTO);
        assertThat(request.getValue().amountMinor()).isEqualTo(1000);
        assertThat(request.getValue().toString()).doesNotContain("5321962145453348");
        assertThat(started.checkoutId()).isEqualTo(checkoutId);
    }

    @Test
    void rejectsAnUnknownCatalogProduct() {
        MerchantStorefrontService service = service(mock(EcommerceSimulatorClient.class));

        assertThatThrownBy(() -> service.createOrder(new MerchantOrderCreateRequest(
                List.of(new MerchantOrderItemRequest("INVENTED", 1)))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("inconnu");
    }

    private MerchantStorefrontService service(EcommerceSimulatorClient client) {
        return new MerchantStorefrontService(client,
                temporaryDirectory.resolve("profile-id").toString(),
                MerchantSiteType.NATIONAL);
    }
}
