package com.staging.sg.waypos.server.service;

import com.staging.sg.common.iso.WayPosKeyExchangeCodec;
import com.staging.sg.common.iso.crypto.JposHsmService;
import com.staging.sg.waypos.server.domain.PosTerminalKey;
import com.staging.sg.waypos.server.domain.PosTerminalProfile;
import com.staging.sg.waypos.server.repository.PosTerminalKeyRepository;
import com.staging.sg.waypos.server.repository.PosTerminalProfileRepository;
import org.jpos.iso.ISOMsg;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WayPosKeyExchangeServiceTest {
    private static final String TAMK =
            "00112233445566778899AABBCCDDEEFF0102030405060708";
    private static final String TPMK =
            "102132435465768798A9BACBDCEDFE0F1122334455667788";

    @Test
    void generatedPairOverridesExistingLocalDatabaseBlocks()
            throws Exception {
        PosTerminalKeyRepository keys = mock(PosTerminalKeyRepository.class);
        PosTerminalProfileRepository terminals =
                mock(PosTerminalProfileRepository.class);
        JposHsmService hsm = mock(JposHsmService.class);
        byte[] tpkBlock = way4Block("P0", "27")
                .getBytes(StandardCharsets.US_ASCII);
        byte[] takBlock = way4Block("M3", "27")
                .getBytes(StandardCharsets.US_ASCII);
        WayPosKeyExchangeService service = new WayPosKeyExchangeService(
                keys, terminals, hsm, true,
                TAMK, TPMK, "00", "00");
        PosTerminalKey oldTak = localKey("TAK", "OLD-TAK");
        PosTerminalKey oldTpk = localKey("TPK", "OLD-TPK");
        when(keys.findByTerminalIdOrderByIdDesc("TERM0001"))
                .thenReturn(List.of(oldTak, oldTpk));
        when(hsm.generateTr31WorkingKey("TAK", 16, TAMK, "27"))
                .thenReturn(new JposHsmService.Tr31KeyResult(
                        "00112233445566778899AABBCCDDEEFF",
                        "A1B2C3", takBlock, 16));
        when(hsm.generateTr31WorkingKey("TPK", 16, TPMK, "27"))
                .thenReturn(new JposHsmService.Tr31KeyResult(
                        "102132435465768798A9BACBDCEDFE0F",
                        "D4E5F6", tpkBlock, 16));
        ISOMsg request = new ISOMsg();
        request.set(41, "TERM0001");

        List<byte[]> fields = service.exchange(
                request, mock(PosTerminalProfile.class));

        assertEquals(1, fields.size());
        assertEquals(292, fields.get(0).length);
        List<WayPosKeyExchangeCodec.KeyBlock> decoded =
                WayPosKeyExchangeCodec.decodeResponse(fields.get(0));
        assertEquals(List.of("TPK", "TAK"), decoded.stream()
                .map(WayPosKeyExchangeCodec.KeyBlock::keyType).toList());
        assertEquals(List.of("27", "27"), decoded.stream()
                .map(WayPosKeyExchangeCodec.KeyBlock::keyId).toList());
        assertEquals(List.of(112, 112), decoded.stream()
                .map(key -> key.ansiX917Block().length).toList());
        verify(keys, times(2)).saveAll(anyList());
    }

    @Test
    void advancesPastExistingNumericKeyId() throws Exception {
        PosTerminalKeyRepository keys = mock(PosTerminalKeyRepository.class);
        JposHsmService hsm = mock(JposHsmService.class);
        PosTerminalKey oldTak = localKey("TAK", "27");
        PosTerminalKey oldTpk = localKey("TPK", "27");
        when(keys.findByTerminalIdOrderByIdDesc("TERM0001"))
                .thenReturn(List.of(oldTak, oldTpk));
        when(hsm.generateTr31WorkingKey("TAK", 16, TAMK, "28"))
                .thenReturn(result("M3", "28"));
        when(hsm.generateTr31WorkingKey("TPK", 16, TPMK, "28"))
                .thenReturn(result("P0", "28"));
        WayPosKeyExchangeService service = new WayPosKeyExchangeService(
                keys, mock(PosTerminalProfileRepository.class), hsm,
                true, TAMK, TPMK, "00", "00");
        ISOMsg request = new ISOMsg();
        request.set(41, "TERM0001");

        List<WayPosKeyExchangeCodec.KeyBlock> decoded =
                WayPosKeyExchangeCodec.decodeResponse(
                        service.exchange(request,
                                mock(PosTerminalProfile.class)).get(0));

        assertEquals(List.of("28", "28"), decoded.stream()
                .map(WayPosKeyExchangeCodec.KeyBlock::keyId).toList());
    }

    private static JposHsmService.Tr31KeyResult result(
            String usage, String id) {
        return new JposHsmService.Tr31KeyResult(
                "00112233445566778899AABBCCDDEEFF", "A1B2C3",
                way4Block(usage, id).getBytes(StandardCharsets.US_ASCII), 16);
    }

    private static PosTerminalKey localKey(String type, String id) {
        return PosTerminalKey.pending(
                "TERM0001", type, id, "T", "ABCDEF", "00",
                "TAK".equals(type) ? "TAMK" : "TPMK",
                "LOCAL-BLOCK".getBytes(StandardCharsets.US_ASCII),
                "00112233445566778899AABBCCDDEEFF", 16,
                "0", null);
    }

    private static String way4Block(String usage, String keyId) {
        return "D0112" + usage + "TN" + keyId + "N0000"
                + "A".repeat(96);
    }
}
