package com.staging.sg.acquiring.service;

import com.staging.sg.acquiring.domain.*;
import com.staging.sg.acquiring.port.EcommerceNetworkCommand;
import com.staging.sg.acquiring.port.EcommerceNetworkPort;
import com.staging.sg.acquiring.repository.*;
import com.staging.sg.common.ecommerce.*;
import com.staging.sg.common.issuing.PaymentIdentifierType;
import com.staging.sg.common.routing.RoutingTransactionResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class EcommerceTransactionServiceTest {
    @Test
    void routesAndPersistsAnUnauthenticatedEcommercePurchase() {
        Fixture fixture = new Fixture();
        when(fixture.network.authorize(any())).thenReturn(
                new RoutingTransactionResponse("TX-1", "APPROVED", "00", "000",
                        "123456", "SWAM_MEMBER", "000000001000", null,
                        false, Map.of("network", "SWAM")));

        EcommercePurchaseResponse response = fixture.service.purchase(
                fixture.request(EcommerceAuthenticationStatus.NOT_PERFORMED,
                        EcommerceNetworkRoute.SWAM));

        assertThat(response.status()).isEqualTo("APPROVED");
        assertThat(response.approvedAmountMinor()).isEqualTo(1000);
        assertThat(response.authenticationStatus())
                .isEqualTo(EcommerceAuthenticationStatus.NOT_PERFORMED);
        ArgumentCaptor<EcommerceNetworkCommand> command =
                ArgumentCaptor.forClass(EcommerceNetworkCommand.class);
        verify(fixture.network).authorize(command.capture());
        assertThat(command.getValue().terminalId()).isEqualTo("ECOM0001");
        assertThat(command.getValue().merchantId()).isEqualTo("MID000000000001");
        verify(fixture.transactions, atLeast(2)).save(any(EcommerceTransaction.class));
        verify(fixture.outbox).save(any(AcquiringOutboxEvent.class));
    }

    @Test
    void refuses3dsDataUntilThe3dsModuleExists() {
        Fixture fixture = new Fixture();
        EcommercePurchaseRequest request = new EcommercePurchaseRequest(
                "1.0", "TX-1", "CORR-1", "IDEM-1", "ACQTEST",
                fixture.profile.id(), "ORDER-1", 1000, "504",
                PaymentIdentifierType.PAN, "5321962145453348", "2912",
                EcommerceNetworkRoute.DMAS_MASTERCARD,
                EcommerceAuthenticationStatus.AUTHENTICATED,
                "05", "CAVV", "DS-1");

        assertThatThrownBy(() -> fixture.service.purchase(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("3DS");
        verifyNoInteractions(fixture.network);
    }

    private static final class Fixture {
        final EcommerceAcceptanceProfileRepository profiles = mock(EcommerceAcceptanceProfileRepository.class);
        final EcommerceStoreRepository stores = mock(EcommerceStoreRepository.class);
        final AcquiringContractRepository contracts = mock(AcquiringContractRepository.class);
        final AcquiringContractDetailRepository details = mock(AcquiringContractDetailRepository.class);
        final EcommerceTransactionRepository transactions = mock(EcommerceTransactionRepository.class);
        final AcquiringOutboxEventRepository outbox = mock(AcquiringOutboxEventRepository.class);
        final EcommerceNetworkPort network = mock(EcommerceNetworkPort.class);
        final EcommerceRouteResolver routes = mock(EcommerceRouteResolver.class);
        final EcommerceStore store;
        final AcquiringContract contract;
        final EcommerceAcceptanceProfile profile;
        final EcommerceTransactionService service;

        Fixture() {
            UUID merchantId = UUID.randomUUID();
            UUID contractId;
            store = EcommerceStore.draft(merchantId, "STORE-1", "Store",
                    "shop.example.test", "https://shop.example.test/return",
                    "https://shop.example.test/notify");
            store.ready();
            store.activate();
            contract = AcquiringContract.merchant("ACQTEST", "ECOM-CONTRACT",
                    merchantId, "SETTLEMENT-1", UUID.randomUUID(), "MAKER",
                    "CONTRACT-IDEM", "0".repeat(64));
            contract.submit();
            contract.approve("CHECKER");
            contractId = contract.id();
            profile = EcommerceAcceptanceProfile.active("ACQTEST", store.id(),
                    contractId, "ECOM0001", "504", "IMMEDIATE");
            AcquiringContractDetail detail = AcquiringContractDetail.of(contractId,
                    "ACQTEST", merchantId, "MID000000000001", "5411", "504",
                    AcceptanceChannel.ECOMMERCE);
            when(profiles.findById(profile.id())).thenReturn(Optional.of(profile));
            when(stores.findById(store.id())).thenReturn(Optional.of(store));
            when(contracts.findById(contract.id())).thenReturn(Optional.of(contract));
            when(details.findById(contract.id())).thenReturn(Optional.of(detail));
            when(transactions.findByAcquirerIdAndIdempotencyKey(any(), any()))
                    .thenReturn(Optional.empty());
            when(transactions.save(any())).thenAnswer(call -> call.getArgument(0));
            when(outbox.save(any())).thenAnswer(call -> call.getArgument(0));
            when(routes.resolve(any(), any())).thenAnswer(call -> call.getArgument(1));
            service = new EcommerceTransactionService(profiles, stores, contracts,
                    details, transactions, outbox, network, routes);
        }

        EcommercePurchaseRequest request(EcommerceAuthenticationStatus status,
                EcommerceNetworkRoute route) {
            return new EcommercePurchaseRequest("1.0", "TX-1", "CORR-1",
                    "IDEM-1", "ACQTEST", profile.id(), "ORDER-1", 1000,
                    "504", PaymentIdentifierType.PAN, "5321962145453348",
                    "2912", route, status, null, null, null);
        }
    }
}
