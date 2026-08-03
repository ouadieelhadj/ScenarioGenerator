package com.staging.sg.waypos.server.service;

import com.staging.sg.common.iso.WayPosKeyExchangeCodec;
import com.staging.sg.common.iso.WayPosPackager;
import com.staging.sg.common.iso.crypto.JposHsmService;
import org.jpos.iso.ISOMsg;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WayPosInitialKeyChangeAuthenticatorTest {
    private static final String TAMK = "11".repeat(24);
    private static final String TPMK = "22".repeat(24);

    @Test
    void acceptsOnlyMatchingTamkAndTpmkKcvs() throws Exception {
        JposHsmService hsm = mock(JposHsmService.class);
        when(hsm.computeKcv(any())).thenAnswer(invocation -> {
            byte[] key = invocation.getArgument(0);
            return key[0] == 0x11 ? "A1B2C3" : "D4E5F6";
        });
        WayPosInitialKeyChangeAuthenticator authenticator =
                new WayPosInitialKeyChangeAuthenticator(
                        hsm, true, TAMK, TPMK, "00", "00");

        assertTrue(authenticator.authenticates(request("D4E5F6")));
        assertFalse(authenticator.authenticates(request("000000")));
    }

    private static ISOMsg request(String tpmkKcv) throws Exception {
        ISOMsg request = new ISOMsg();
        request.setPackager(new WayPosPackager());
        request.setMTI("0800");
        request.set(3, "960000");
        request.set(7, "0803160000");
        request.set(11, "123456");
        request.set(41, "TERM0001");
        request.set(48, WayPosKeyExchangeCodec.encodeStatusDetails(List.of(
                new WayPosKeyExchangeCodec.KeyStatusDetails(
                        "00", "0", "TAMK", "A1B2C3", "C", "0"),
                new WayPosKeyExchangeCodec.KeyStatusDetails(
                        "00", "0", "TPMK", tpmkKcv, "C", "0"))));
        request.set(63, "007SV1.0.0");
        return request;
    }
}
