package com.staging.sg.common.service;

import com.staging.sg.common.entity.DmcsAcquirerClearingTransaction;
import com.staging.sg.common.entity.McDmasMemberTransaction;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DmcAuthorizationToClearingMapperTest {

    @Test
    void mapsAuthorizationToIndependentFirstPresentment() {
        McDmasMemberTransaction authorization = new McDmasMemberTransaction();
        authorization.setId(42L);
        authorization.setPan("5413330089012345");
        authorization.setMaskedPan("541333******2345");
        authorization.setProcessingCode("000000");
        authorization.setAmount(12_345L);
        authorization.setLocalDate("0728");
        authorization.setLocalTime("153000");
        authorization.setExpiry("2906");
        authorization.setPosEntryMode("052");
        authorization.setPosData("000001000030050420100");
        authorization.setMcc("5999");
        authorization.setAcquiringInstitutionId("22905");
        authorization.setRrn("620928123456");
        authorization.setAuthorizationCode("ABC123");
        authorization.setCurrency("504");

        DmcsAcquirerClearingTransaction clearing =
                DmcAuthorizationToClearingMapper.populateFirstPresentment(
                        new DmcsAcquirerClearingTransaction(), authorization,
                        LocalDate.of(2026, 7, 28), "555555", "22905");

        assertEquals(42L, clearing.getLocalAuthorizationId());
        assertEquals("FIRST_PRESENTMENT", clearing.getLifecycleStage());
        assertEquals("READY", clearing.getStatus());
        assertEquals("260728153000", clearing.getTransactionDatetime());
        assertEquals("M01101C99000", clearing.getPosDataCode());
        assertEquals(64, clearing.getCorrelationKey().length());
        assertNull(clearing.getAcquirerReference(), "DE31 reste differe explicitement");
    }

    @Test
    void mapsKnownCisPanEntryModesFromGuide() {
        assertEquals('C', DmcAuthorizationToClearingMapper
                .toDmcPosDataCode("052", null, null).charAt(6));
        assertEquals('M', DmcAuthorizationToClearingMapper
                .toDmcPosDataCode("072", null, null).charAt(6));
        assertEquals('B', DmcAuthorizationToClearingMapper
                .toDmcPosDataCode("902", null, null).charAt(6));
    }

    @Test
    void parsesStrictDmasOriginalDataElements() {
        var original = McDmasAuthorizationJournalMapper.parseOriginalDataElements(
                "010012345607291530000000002290500000000000");

        assertEquals("0100", original.mti());
        assertEquals("123456", original.stan());
        assertEquals("0729153000", original.transmissionDatetime());
        assertEquals("00000022905", original.acquiringInstitutionId());
        assertEquals("00000000000", original.forwardingInstitutionId());

        assertThrows(IllegalArgumentException.class,
                () -> McDmasAuthorizationJournalMapper
                        .parseOriginalDataElements("0100123456"));
    }
}
