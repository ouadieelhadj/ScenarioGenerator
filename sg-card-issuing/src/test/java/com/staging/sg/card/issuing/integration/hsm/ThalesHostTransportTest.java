package com.staging.sg.card.issuing.integration.hsm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.staging.sg.card.issuing.domain.IssuingInterfaceDirection;
import com.staging.sg.card.issuing.domain.IssuingInterfaceEndpoint;
import com.staging.sg.card.issuing.domain.IssuingInterfaceProtocol;
import com.staging.sg.card.issuing.domain.IssuingInterfaceType;
import com.staging.sg.card.issuing.service.IssuingEndpointResolver;
import org.junit.jupiter.api.Test;

import java.io.DataInputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ThalesHostTransportTest {
    @Test
    void exchangesBinaryLengthFramedPayloadWithoutInterpretingSecrets()
            throws Exception {
        try (ServerSocket server = new ServerSocket(
                0, 1, InetAddress.getByName("127.0.0.1"))) {
            CompletableFuture<String> received = CompletableFuture.supplyAsync(
                    () -> receiveAndReply(server));
            IssuingEndpointResolver endpoints =
                    mock(IssuingEndpointResolver.class);
            when(endpoints.requireActive("BANK1", IssuingInterfaceType.HSM))
                    .thenReturn(endpoint(server.getLocalPort()));
            ThalesHostTransport transport =
                    new ThalesHostTransport(endpoints, new ObjectMapper());

            byte[] response = transport.exchange(
                    "BANK1", "0000NC".getBytes(StandardCharsets.US_ASCII));

            assertThat(received.get()).isEqualTo("0000NC");
            assertThat(new String(response, StandardCharsets.US_ASCII))
                    .isEqualTo("0000ND00");
        }
    }

    private static String receiveAndReply(ServerSocket server) {
        try (var socket = server.accept()) {
            DataInputStream input =
                    new DataInputStream(socket.getInputStream());
            int length = input.readUnsignedShort();
            String request = new String(
                    input.readNBytes(length), StandardCharsets.US_ASCII);
            byte[] response =
                    "0000ND00".getBytes(StandardCharsets.US_ASCII);
            socket.getOutputStream().write(response.length >>> 8);
            socket.getOutputStream().write(response.length & 0xff);
            socket.getOutputStream().write(response);
            socket.getOutputStream().flush();
            return request;
        } catch (Exception failure) {
            throw new IllegalStateException(failure);
        }
    }

    private static IssuingInterfaceEndpoint endpoint(int port) {
        return IssuingInterfaceEndpoint.draft(
                "BANK1", IssuingInterfaceType.HSM, 1,
                IssuingInterfaceDirection.OUTBOUND,
                IssuingInterfaceProtocol.TCP, "127.0.0.1", port,
                null, 500, 1000, null, "vault://hsm",
                "{\"framing\":\"BINARY_2_BE\",\"maxMessageBytes\":1024}",
                "maker", "idem-hsm", "fingerprint");
    }
}
