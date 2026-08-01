package com.staging.sg.acquiring.domain;

import com.staging.sg.common.contract.PaymentContractStatus;
import com.staging.sg.common.contract.PaymentContractType;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AcquiringLifecycleTest {
    @Test
    void merchantAndProductEnforceMakerChecker() {
        AcceptanceProduct product = AcceptanceProduct.draft(
                "ACQ1", "POS_STD", 1, AcceptanceChannel.BOTH, "504", "maker");
        product.submit();
        assertThrows(IllegalStateException.class, () -> product.approve("maker"));
        product.approve("checker");
        assertTrue(product.isActive());

        Merchant merchant = Merchant.draft("ACQ1", "Merchant SARL", "Merchant",
                "RC-100", "MA", "5411", "maker", "idem-1", "a".repeat(64));
        merchant.submit();
        assertThrows(IllegalStateException.class, () -> merchant.approve("maker"));
        merchant.approve("checker");
        assertEquals(ApprovalStatus.ACTIVE, merchant.status());
    }

    @Test
    void merchantAndDeviceContractsShareLifecycleAndTableDiscriminator() {
        UUID merchantId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        AcquiringContract merchant = AcquiringContract.merchant("ACQ1", "MC-1",
                merchantId, "SETTLEMENT-1", productId, "maker", "mc-idem",
                "b".repeat(64));
        merchant.submit();
        merchant.approve("checker");

        AcquiringContract device = AcquiringContract.device("ACQ1", "DC-1",
                merchantId, merchant.id(), productId, "maker-2", "dc-idem",
                "c".repeat(64));
        device.submit();
        device.approve("checker-2");

        assertEquals(PaymentContractType.ACQUIRING_MERCHANT, merchant.contractType());
        assertEquals(PaymentContractType.ACQUIRING_DEVICE, device.contractType());
        assertEquals(merchant.id(), device.parentContractId());
        assertEquals(PaymentContractStatus.ACTIVE, device.status());
        assertEquals("payment_contract",
                AcquiringContract.class.getAnnotation(jakarta.persistence.Table.class).name());
    }

    @Test
    void terminalAndEcommerceLifecycleAreFailClosed() {
        TerminalDevice terminal = TerminalDevice.inStock("ACQ1", "SN-1", "MODEL-1");
        assertThrows(IllegalStateException.class, terminal::activate);
        terminal.assign();
        terminal.provisioning();
        terminal.ready();
        terminal.activate();
        assertEquals(TerminalStatus.ACTIVE, terminal.status());

        assertThrows(IllegalArgumentException.class, () -> EcommerceStore.draft(
                UUID.randomUUID(), "STORE", "Store", "shop.example.com",
                "http://shop.example.com/return", "https://shop.example.com/hook"));
        EcommerceStore store = EcommerceStore.draft(UUID.randomUUID(), "STORE", "Store",
                "shop.example.com", "https://shop.example.com/return",
                "https://shop.example.com/hook");
        store.ready();
        store.activate();
        assertEquals(EcommerceStatus.ACTIVE, store.status());
    }
}
