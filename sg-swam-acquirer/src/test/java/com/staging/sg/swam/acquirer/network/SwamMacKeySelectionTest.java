package com.staging.sg.swam.acquirer.network;

import com.staging.sg.common.entity.SwamAcqKey;
import com.staging.sg.common.entity.SwamKek;
import com.staging.sg.common.iso.SwamPackager;
import com.staging.sg.common.iso.crypto.JposHsmService;
import com.staging.sg.common.repository.SwamAcqKeyRepository;
import com.staging.sg.common.repository.SwamKekRepository;
import org.jpos.iso.ISOMsg;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SwamMacKeySelectionTest {
    @Test
    void ack811UsesZmkEvenWhenAnOldMakIsStillActive() throws Exception {
        Fixture fixture = fixture();
        ISOMsg ack = ack("811");

        fixture.swamMac.apply(ack);

        verify(fixture.hsm).generateMacZmk(any(byte[].class), eq("0123456789ABCDEFFEDCBA9876543210"));
        verify(fixture.hsm, never()).generateMac(
                any(byte[].class), anyString(), anyString(), anyInt());
    }

    @Test
    void ack899UsesZmkEvenWhenMakIsActive() throws Exception {
        Fixture fixture = fixture();
        ISOMsg ack = ack("899");

        fixture.swamMac.apply(ack);

        verify(fixture.hsm).generateMacZmk(
                any(byte[].class), eq("0123456789ABCDEFFEDCBA9876543210"));
        verify(fixture.hsm, never()).generateMac(
                any(byte[].class), anyString(), anyString(), anyInt());
    }

    private static Fixture fixture() throws Exception {
        JposHsmService hsm = mock(JposHsmService.class);
        SwamAcqKeyRepository keyRepository =
                mock(SwamAcqKeyRepository.class);
        SwamKekRepository kekRepository = mock(SwamKekRepository.class);
        SwamAcqKey mak = new SwamAcqKey();
        mak.setMemberGroupId("TESTGRP01");
        mak.setKeyType("MAK");
        mak.setKeyUnderLmk("MAK_UNDER_LMK");
        mak.setKcv("ABCDEF");
        mak.setKeyLength(16);
        SwamKek kek = new SwamKek();
        kek.setMemberGroupId("TESTGRP01");
        kek.setKekClear("0123456789ABCDEFFEDCBA9876543210");
        when(keyRepository.findByMemberGroupIdAndKeyTypeAndStatus(
                "TESTGRP01", "MAK", "ACTIVE")).thenReturn(Optional.of(mak));
        when(kekRepository.findByMemberGroupId("TESTGRP01"))
                .thenReturn(Optional.of(kek));
        when(hsm.generateMacZmk(any(byte[].class), anyString()))
                .thenReturn(new byte[8]);
        when(hsm.generateMac(any(byte[].class), anyString(), anyString(), anyInt()))
                .thenReturn(new byte[8]);
        SwamMac swamMac = new SwamMac(hsm, keyRepository, kekRepository);
        ReflectionTestUtils.setField(swamMac, "macLength", 4);
        return new Fixture(swamMac, hsm);
    }

    private static ISOMsg ack(String function) throws Exception {
        ISOMsg ack = new ISOMsg();
        ack.setPackager(new SwamPackager());
        ack.setMTI("1814");
        ack.set(7, "2607291511");
        ack.set(11, "580001");
        ack.set(24, function);
        ack.set(39, "800");
        return ack;
    }

    private record Fixture(SwamMac swamMac, JposHsmService hsm) {
    }
}
