package com.staging.sg.waypos.server.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.staging.sg.common.routing.RoutingTransactionRequest;
import com.staging.sg.common.routing.RoutingTransactionResponse;
import com.staging.sg.waypos.server.config.WayPosProperties;
import com.staging.sg.waypos.server.domain.PosOutbox;
import com.staging.sg.waypos.server.repository.PosOutboxRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PosRecoveryServiceTest {
    private static final String TEST_KEY =
            "00112233445566778899AABBCCDDEEFF"
            + "102132435465768798A9BACBDCEDFE0F";

    @Test
    void encryptsRecoveryAndBuildsAutomaticReversalAfterUnknownDebit()
            throws Exception {
        PosOutboxRepository repository = mock(PosOutboxRepository.class);
        WayPosPayloadCipher cipher = cipher();
        ObjectMapper json = new ObjectMapper();
        PosRecoveryService service =
                new PosRecoveryService(repository, cipher, json);

        service.scheduleIfNeeded(request("DEBIT", "0200"),
                unknown(), "DMAS_MEMBER");

        ArgumentCaptor<PosOutbox> saved =
                ArgumentCaptor.forClass(PosOutbox.class);
        verify(repository).save(saved.capture());
        PosOutbox item = saved.getValue();
        String plaintext = cipher.decrypt(
                item.getPayloadCiphertext(), item.getPayloadIv(),
                item.getPayloadKeyId());
        RoutingTransactionRequest recovery =
                json.readValue(plaintext, RoutingTransactionRequest.class);
        assertEquals("AUTOMATIC_REVERSAL", item.getMessageType());
        assertEquals("REVERSAL", recovery.operation());
        assertEquals("0420", recovery.sourceMti());
        assertEquals("402", recovery.attributes().get("networkId"));
        assertEquals("tx", recovery.originalTransactionId());
        assertNull(recovery.pinBlockHex());
    }

    @Test
    void authenticatedEncryptionRejectsTampering() {
        WayPosPayloadCipher cipher = cipher();
        WayPosPayloadCipher.Encrypted encrypted = cipher.encrypt("sensitive");
        byte[] modified = encrypted.ciphertext().clone();
        modified[0] ^= 1;
        assertThrows(IllegalStateException.class, () -> cipher.decrypt(
                modified, encrypted.iv(), encrypted.keyId()));
    }

    @Test
    void dispatcherMarksRecoveryDeliveredAndAppliesLinkedOutcome() {
        PosOutboxRepository repository = mock(PosOutboxRepository.class);
        WayPosPayloadCipher cipher = cipher();
        ObjectMapper json = new ObjectMapper();
        RoutingTransactionRequest request = request("REVERSAL", "0421");
        WayPosPayloadCipher.Encrypted encrypted;
        try {
            encrypted = cipher.encrypt(json.writeValueAsString(request));
        } catch (Exception e) {
            throw new AssertionError(e);
        }
        PosOutbox item = PosOutbox.pending(
                "tx", "REPEAT", "DMAS_MEMBER",
                encrypted.ciphertext(), encrypted.iv(), encrypted.keyId());
        NetworkRoutingConnector network = mock(NetworkRoutingConnector.class);
        RoutingTransactionResponse response = new RoutingTransactionResponse(
                "tx", "APPROVED", "00", "00", null,
                "DMAS_MEMBER", null, null, false, Map.of());
        when(network.send("DMAS_MEMBER", request)).thenReturn(response);
        PosJournalService journal = mock(PosJournalService.class);
        PosOutboxDispatcher dispatcher = new PosOutboxDispatcher(
                repository, cipher, json, network, journal);

        dispatcher.dispatch(item);

        assertEquals("DELIVERED", item.getStatus());
        verify(journal).applyLinkedOutcome(request, response);
        verify(repository).save(item);
    }

    private static WayPosPayloadCipher cipher() {
        return new WayPosPayloadCipher(new WayPosProperties(
                8531, 55, "pepper", TEST_KEY, Map.of()));
    }

    private static RoutingTransactionRequest request(
            String operation, String mti) {
        return new RoutingTransactionRequest(
                "1.0", "tx", "corr", "idem", operation, mti,
                "000000", "5321962145453348", "2912",
                "000000001000", "504", "000001", "123456000001",
                "TERM0001", "MERCHANT0000001", "0011223344556677",
                null, null, Map.of("transmissionDateTime", "0730113000"));
    }

    private static RoutingTransactionResponse unknown() {
        return new RoutingTransactionResponse(
                "tx", "UNKNOWN", "91", null, null,
                "DMAS_MEMBER", null, null, true, Map.of());
    }
}
