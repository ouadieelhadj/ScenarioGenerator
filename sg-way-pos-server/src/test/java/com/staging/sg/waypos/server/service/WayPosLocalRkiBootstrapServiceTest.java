package com.staging.sg.waypos.server.service;

import com.staging.sg.common.iso.crypto.HsmService;
import com.staging.sg.common.iso.crypto.JposHsmService;
import org.jpos.security.SecureDESKey;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WayPosLocalRkiBootstrapServiceTest {
    private static final String INITIAL_TAK = "01".repeat(16);
    private static final String TAMK = "11".repeat(24);
    private static final String TPMK = "22".repeat(24);

    @Test
    void provisionsObservedWay4BlocksAndProvesWireFormat() throws Exception {
        JposHsmService hsm = mock(JposHsmService.class);
        SecureDESKey initialUnderLmk = mock(SecureDESKey.class);
        when(initialUnderLmk.getKeyBytes()).thenReturn(new byte[16]);
        when(hsm.formClearKey("TAK", INITIAL_TAK))
                .thenReturn(initialUnderLmk);
        when(hsm.computeKcv(any())).thenReturn("A1B2C3");
        when(hsm.generateWorkingKey(eq("TAK"), eq(16), eq(TAMK)))
                .thenReturn(generated("TAK"));
        when(hsm.generateWorkingKey(eq("TPK"), eq(16), eq(TPMK)))
                .thenReturn(generated("TPK"));
        WayPosWorkingKeyBootstrapService workingKeys =
                mock(WayPosWorkingKeyBootstrapService.class);
        WayPosKeyExchangeService exchange = mock(WayPosKeyExchangeService.class);
        WayPosLocalRkiBootstrapService service =
                new WayPosLocalRkiBootstrapService(
                        hsm, workingKeys, exchange,
                        INITIAL_TAK, TAMK, TPMK, "00", "00", "27",
                        block("M3", 'A'), block("P0", 'B'));

        Map<String, Object> result = service.bootstrap("12488881");

        assertEquals("WAY4_F20_DF40_2", result.get("wireFormat"));
        assertEquals("27", result.get("keyId"));
        assertEquals(292, result.get("de48Length"));
        ArgumentCaptor<WayPosKeyExchangeService.ProvisionedKey> captor =
                ArgumentCaptor.forClass(
                        WayPosKeyExchangeService.ProvisionedKey.class);
        verify(exchange, times(2)).provision(captor.capture());
        List<WayPosKeyExchangeService.ProvisionedKey> keys =
                captor.getAllValues();
        assertEquals(List.of("TAK", "TPK"),
                keys.stream().map(
                        WayPosKeyExchangeService.ProvisionedKey::keyType).toList());
        assertEquals(List.of("27", "27"),
                keys.stream().map(
                        WayPosKeyExchangeService.ProvisionedKey::keyId).toList());
        assertEquals(List.of(112, 112),
                keys.stream().map(key -> key.ansiX917Block().length).toList());
    }

    private static HsmService.KeyResult generated(String type) {
        HsmService.KeyResult result = new HsmService.KeyResult();
        result.keyUnderKek = new byte[16];
        result.keyUnderLmkHex = "TAK".equals(type)
                ? "01".repeat(16) : "02".repeat(16);
        result.kcv = "TAK".equals(type) ? "111111" : "222222";
        return result;
    }

    private static String block(String usage, char fill) {
        return "D0112" + usage + "TN27N0000"
                + String.valueOf(fill).repeat(96);
    }
}
