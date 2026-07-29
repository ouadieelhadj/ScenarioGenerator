package com.staging.sg.dmcs.common.ipm;

import com.staging.sg.common.entity.DmcsIssuerClearingTransaction;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DmcOutgoingDisputeMapperTest {
    @Test
    void mapsChargebackFromRealParentReferences() {
        var parent = parent();
        var command = new DmcOutgoingDisputeMapper.DisputeCommand(
                "450", 10_000L, "4808",
                "ISSUER-REFERENCE-DATA-12345678901234567890",
                requiredPds(), LocalDate.of(2026, 7, 29));

        var result = DmcOutgoingDisputeMapper.populate(
                new DmcsIssuerClearingTransaction(), parent, command,
                "FIRST_CHARGEBACK", "555555", "22905");

        assertEquals("12345678901234567890123",
                result.getAcquirerReference());
        assertEquals("000000010000000000010000",
                result.getOriginalAmounts());
        assertEquals("1442", result.getMti());
        assertEquals("00000002", result.getMessageNumber());
    }

    @Test
    void blocksCycleWhenParentHasNoRealArn() {
        var parent = parent();
        parent.setAcquirerReference(null);
        var command = new DmcOutgoingDisputeMapper.DisputeCommand(
                "450", 10_000L, "4808",
                "ISSUER-REFERENCE-DATA-12345678901234567890",
                requiredPds(), LocalDate.of(2026, 7, 29));

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> DmcOutgoingDisputeMapper.populate(
                        new DmcsIssuerClearingTransaction(), parent, command,
                        "FIRST_CHARGEBACK", "555555", "22905"));
        assertEquals("DE31/ARN réel absent", error.getMessage());
    }

    private static DmcsIssuerClearingTransaction parent() {
        var parent = new DmcsIssuerClearingTransaction();
        parent.setId(10L);
        parent.setBusinessDate(LocalDate.of(2026, 7, 29));
        parent.setCorrelationKey("FP:10");
        parent.setLifecycleStage("FIRST_PRESENTMENT");
        parent.setPan("5413330089012345");
        parent.setMaskedPan("541333******2345");
        parent.setProcessingCode("000000");
        parent.setAmount(10_000L);
        parent.setReconciliationAmount(10_000L);
        parent.setTransactionDatetime("260729153000");
        parent.setExpiry("2906");
        parent.setPosDataCode("M01101C99000");
        parent.setMcc("5999");
        parent.setAcquirerReference("12345678901234567890123");
        parent.setAcquiringInstitutionId("22905");
        parent.setForwardingInstitutionId("555555");
        parent.setRrn("620928123456");
        parent.setAuthorizationCode("ABC123");
        parent.setTerminalId("TERM0001");
        parent.setAcceptorId("MERCHANT0000001");
        parent.setAcceptorNameLocation("TEST MERCHANT CASABLANCA MA");
        parent.setCurrency("504");
        parent.setReconciliationCurrency("504");
        return parent;
    }

    private static String requiredPds() {
        return DmcPdsCodec.concat(
                DmcPdsCodec.encode(148, "0202"),
                DmcPdsCodec.encode(149, "50425042"));
    }
}
