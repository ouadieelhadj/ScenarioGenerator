package com.staging.sg.acquiring.service;

import com.staging.sg.acquiring.domain.*;
import com.staging.sg.acquiring.repository.*;
import com.staging.sg.common.contract.PaymentContractStatus;
import com.staging.sg.common.contract.PaymentContractType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AcquiringAdministrationServiceTest {
    private final AcceptanceProductRepository products = mock(AcceptanceProductRepository.class);
    private final MerchantRepository merchants = mock(MerchantRepository.class);
    private final MerchantOutletRepository outlets = mock(MerchantOutletRepository.class);
    private final AcquiringContractRepository contracts = mock(AcquiringContractRepository.class);
    private final AcquiringContractDetailRepository contractDetails = mock(AcquiringContractDetailRepository.class);
    private final AcquiringDeviceContractDetailRepository deviceDetails = mock(AcquiringDeviceContractDetailRepository.class);
    private final TerminalDeviceRepository terminals = mock(TerminalDeviceRepository.class);
    private final TerminalAssignmentRepository assignments = mock(TerminalAssignmentRepository.class);
    private final EcommerceStoreRepository stores = mock(EcommerceStoreRepository.class);
    private final EcommerceAcceptanceProfileRepository profiles = mock(EcommerceAcceptanceProfileRepository.class);
    private final AcquiringOutboxEventRepository outbox = mock(AcquiringOutboxEventRepository.class);
    private AcquiringAdministrationService service;

    @BeforeEach
    void setUp() {
        reset(products, merchants, outlets, contracts, contractDetails, deviceDetails,
                terminals, assignments, stores, profiles, outbox);
        service = new AcquiringAdministrationService(products, merchants, outlets,
                contracts, contractDetails, deviceDetails, terminals, assignments,
                stores, profiles, outbox);
    }

    @Test
    void merchantCreationIsIdempotentAndRejectsPayloadChange() {
        when(merchants.findByAcquirerIdAndCreatedByAndCreationIdempotencyKey(
                "ACQ1", "maker", "idem-1")).thenReturn(Optional.empty());
        Merchant created = service.createMerchant("ACQ1", "Legal", "Trade", "RC1",
                "MA", "5411", "maker", "idem-1", "corr-1");
        verify(merchants).save(created);
        verify(outbox).save(any());

        when(merchants.findByAcquirerIdAndCreatedByAndCreationIdempotencyKey(
                "ACQ1", "maker", "idem-1")).thenReturn(Optional.of(created));
        assertSame(created, service.createMerchant("ACQ1", "Legal", "Trade", "RC1",
                "MA", "5411", "maker", "idem-1", "corr-2"));
        assertThrows(IllegalStateException.class, () -> service.createMerchant(
                "ACQ1", "Other", "Trade", "RC1", "MA", "5411",
                "maker", "idem-1", "corr-3"));
    }

    @Test
    void createsMerchantAndDeviceContractsInSameContractRepository() {
        AcceptanceProduct product = activeProduct();
        Merchant merchant = activeMerchant();
        MerchantOutlet outlet = MerchantOutlet.active(merchant.id(), "OUT-1", "Outlet",
                "1 Main Street", "MA");
        when(products.findById(product.id())).thenReturn(Optional.of(product));
        when(merchants.findById(merchant.id())).thenReturn(Optional.of(merchant));
        when(outlets.findById(outlet.id())).thenReturn(Optional.of(outlet));
        when(contracts.findByInstitutionIdAndCreatedByAndCreationIdempotencyKey(
                any(), any(), any())).thenReturn(Optional.empty());

        AcquiringContract parent = service.createMerchantContract("ACQ1", "MC-1",
                merchant.id(), "SETTLEMENT-1", product.id(), "MERCHANT0000001",
                "5411", "504", AcceptanceChannel.BOTH, "maker-2", "idem-mc", "corr-mc");
        parent.submit();
        parent.approve("checker-2");
        when(contracts.findById(parent.id())).thenReturn(Optional.of(parent));

        AcquiringContract device = service.createDeviceContract("ACQ1", "DC-1",
                merchant.id(), parent.id(), product.id(), outlet.id(), "TERM0001",
                AcceptanceChannel.TPE, true, "BIN", true,
                "maker-3", "idem-dc", "corr-dc");

        assertEquals(PaymentContractType.ACQUIRING_MERCHANT, parent.contractType());
        assertEquals(PaymentContractType.ACQUIRING_DEVICE, device.contractType());
        assertEquals(parent.id(), device.parentContractId());
        verify(contractDetails).save(any(AcquiringContractDetail.class));
        verify(deviceDetails).save(any(AcquiringDeviceContractDetail.class));
        verify(contracts, times(2)).save(any(AcquiringContract.class));
    }

    @Test
    void deviceContractCannotUseInactiveParent() {
        AcceptanceProduct product = activeProduct();
        Merchant merchant = activeMerchant();
        MerchantOutlet outlet = MerchantOutlet.active(merchant.id(), "OUT-1", "Outlet",
                "1 Main Street", "MA");
        AcquiringContract parent = AcquiringContract.merchant("ACQ1", "MC-1",
                merchant.id(), "SETTLEMENT", product.id(), "maker", "idem-parent",
                "d".repeat(64));
        when(products.findById(product.id())).thenReturn(Optional.of(product));
        when(merchants.findById(merchant.id())).thenReturn(Optional.of(merchant));
        when(outlets.findById(outlet.id())).thenReturn(Optional.of(outlet));
        when(contracts.findById(parent.id())).thenReturn(Optional.of(parent));

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> service.createDeviceContract("ACQ1", "DC-1", merchant.id(),
                        parent.id(), product.id(), outlet.id(), "TERM0001",
                        AcceptanceChannel.TPE, false, "BIN", false,
                        "maker-2", "idem-device", "corr"));
        assertTrue(error.getMessage().contains("active merchant acquiring contract"));
    }

    private static AcceptanceProduct activeProduct() {
        AcceptanceProduct product = AcceptanceProduct.draft("ACQ1", "POS", 1,
                AcceptanceChannel.BOTH, "504", "product-maker");
        product.submit();
        product.approve("product-checker");
        return product;
    }

    private static Merchant activeMerchant() {
        Merchant merchant = Merchant.draft("ACQ1", "Legal", "Trade", "RC1", "MA",
                "5411", "merchant-maker", "merchant-idem", "e".repeat(64));
        merchant.submit();
        merchant.approve("merchant-checker");
        assertEquals(PaymentContractStatus.ACTIVE.name(), merchant.status().name());
        return merchant;
    }
}
