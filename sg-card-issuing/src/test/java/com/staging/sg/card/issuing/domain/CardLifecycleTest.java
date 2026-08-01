package com.staging.sg.card.issuing.domain;

import com.staging.sg.common.contract.PaymentContractStatus;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CardLifecycleTest {
    @Test
    void productRequiresApprovalBeforeActivation() {
        CardProduct product = product();

        assertThrows(IllegalStateException.class, product::activate);
        assertThrows(IllegalStateException.class, () -> product.approve("maker-1"));
        product.approve("checker-1");
        product.activate();

        assertEquals(CardProductStatus.ACTIVE, product.status());
    }

    @Test
    void contractRequiresMakerCheckerSequence() {
        CardContract contract = contract();

        assertEquals("payment_contract",
                CardContract.class.getAnnotation(jakarta.persistence.Table.class).name());
        assertEquals(com.staging.sg.common.contract.PaymentContractType.ISSUING_CARD,
                contract.contractType());

        assertThrows(IllegalStateException.class, () -> contract.approve("checker-1"));
        contract.submit();
        assertThrows(IllegalStateException.class, () -> contract.approve("maker-1"));
        contract.approve("checker-1");

        assertEquals(PaymentContractStatus.ACTIVE, contract.status());
    }

    @Test
    void instrumentCannotActivateAgainstInactiveContract() {
        CardInstrument instrument = CardInstrument.inactive(
                "ISSUER-1", UUID.randomUUID(), "vault-ref-1",
                "532196******3348", "2912", "maker-1",
                "idem-card", "fingerprint");

        assertThrows(IllegalStateException.class,
                () -> instrument.activate(PaymentContractStatus.SUSPENDED));
        instrument.activate(PaymentContractStatus.ACTIVE);

        assertEquals(CardInstrumentStatus.ACTIVE, instrument.status());
    }

    private static CardProduct product() {
        return CardProduct.draft(
                "ISSUER-1", "DEBIT-STD", 1, CardType.DEBIT, "504",
                true, true, true, "maker-1", "idem-product", "fingerprint");
    }

    private static CardContract contract() {
        return CardContract.draft(
                "ISSUER-1", "CONTRACT-1", "CUSTOMER-1", "HOLDER-1",
                "ACCOUNT-1", UUID.randomUUID(), "maker-1",
                "idem-contract", "fingerprint");
    }
}
