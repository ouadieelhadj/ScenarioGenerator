package com.staging.sg.dmcs.common.ipm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DmcDisputeMessageFactoryTest {
    private final DmcDisputeMessageFactory factory =
            new DmcDisputeMessageFactory(new DmcIpmPackager());

    @Test
    void buildsFullFirstChargebackWithRealLifecycleReferences() throws Exception {
        var message = factory.firstChargeback(data("450", 10_000L), 2);

        assertEquals("1442", message.getMTI());
        assertEquals("450", message.getString(24));
        assertEquals("4808", message.getString(25));
        assertEquals("000000010000000000010000", message.getString(30));
        assertEquals("12345678901234567890123", message.getString(31));
        assertEquals("00000002", message.getString(71));
    }

    @Test
    void buildsPartialSecondPresentment() throws Exception {
        var message = factory.secondPresentment(data("282", 4_000L), 3);

        assertEquals("1240", message.getMTI());
        assertEquals("282", message.getString(24));
        assertEquals("000000004000", message.getString(4));
    }

    @Test
    void refusesMissingArnInsteadOfInventingOne() {
        var source = data("450", 10_000L);
        var withoutArn = new DmcDisputeMessageFactory.DisputeData(
                source.functionCode(), source.pan(), source.processingCode(),
                source.amount(), source.transactionDatetime(), source.expiry(),
                source.posDataCode(), source.messageReasonCode(), source.mcc(),
                source.originalAmounts(), null, source.acquiringInstitutionId(),
                source.forwardingInstitutionId(), source.rrn(),
                source.authorizationCode(), source.terminalId(),
                source.acceptorId(), source.acceptorNameLocation(),
                source.currency(), source.destinationId(), source.originId(),
                source.issuerReference(), source.pdsData());

        assertThrows(IllegalArgumentException.class,
                () -> factory.firstChargeback(withoutArn, 2));
    }

    @Test
    void refusesMissingOriginalAmountPds() {
        var source = data("450", 10_000L);
        var withoutPds = new DmcDisputeMessageFactory.DisputeData(
                source.functionCode(), source.pan(), source.processingCode(),
                source.amount(), source.transactionDatetime(), source.expiry(),
                source.posDataCode(), source.messageReasonCode(), source.mcc(),
                source.originalAmounts(), source.acquirerReference(),
                source.acquiringInstitutionId(),
                source.forwardingInstitutionId(), source.rrn(),
                source.authorizationCode(), source.terminalId(),
                source.acceptorId(), source.acceptorNameLocation(),
                source.currency(), source.destinationId(), source.originId(),
                source.issuerReference(), DmcPdsCodec.encode(122, "T"));

        assertThrows(IllegalArgumentException.class,
                () -> factory.firstChargeback(withoutPds, 2));
    }

    private DmcDisputeMessageFactory.DisputeData data(
            String functionCode, long amount) {
        String pds = DmcPdsCodec.concat(
                DmcPdsCodec.encode(148, "0202"),
                DmcPdsCodec.encode(149, "50425042"));
        return new DmcDisputeMessageFactory.DisputeData(
                functionCode,
                "5413330089012345",
                "000000",
                amount,
                "260729153000",
                "2906",
                "M01101C99000",
                "4808",
                "5999",
                "000000010000000000010000",
                "12345678901234567890123",
                "22905",
                "555555",
                "620928123456",
                "ABC123",
                "TERM0001",
                "MERCHANT0000001",
                "TEST MERCHANT CASABLANCA MA",
                "504",
                "555555",
                "22905",
                "ISSUER-REFERENCE-DATA-12345678901234567890",
                pds);
    }
}
