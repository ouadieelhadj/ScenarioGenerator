package com.staging.sg.common.service;

import com.staging.sg.common.entity.McDmasMemberTransaction;
import org.jpos.iso.ISOMsg;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McDmasAuthorizationJournalMapperTest {

    @Test
    void excludesApprovedPreauthorizationRequestFromClearing() throws Exception {
        McDmasMemberTransaction transaction = populate("0100", "4");

        assertTrue(transaction.isApproved());
        assertFalse(transaction.isClearingEligible());
    }

    @Test
    void includesApprovedCompletionAdviceInClearing() throws Exception {
        McDmasMemberTransaction transaction = populate("0120", "4");

        assertTrue(transaction.isApproved());
        assertTrue(transaction.isClearingEligible());
    }

    private McDmasMemberTransaction populate(String mti, String posStatus)
            throws Exception {
        ISOMsg request = new ISOMsg();
        request.setMTI(mti);
        request.set(2, "5413330089012345");
        request.set(3, "000000");
        request.set(4, "000000001000");
        request.set(7, "0729153000");
        request.set(11, "123456");
        request.set(32, "022905");
        request.set(49, "504");
        request.set(61, "000000" + posStatus + "00000");

        ISOMsg response = new ISOMsg();
        response.setMTI("0120".equals(mti) ? "0130" : "0110");
        response.set(39, "00");

        return McDmasAuthorizationJournalMapper.populate(
                new McDmasMemberTransaction(), request, response,
                "022905", "TESTGRP01",
                LocalDateTime.of(2026, 7, 29, 15, 30),
                LocalDateTime.of(2026, 7, 29, 15, 30, 1));
    }
}
