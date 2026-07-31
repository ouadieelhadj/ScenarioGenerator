package com.staging.sg.card.issuing.integration.hsm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.staging.sg.card.issuing.domain.IssuingInterfaceEndpoint;
import com.staging.sg.card.issuing.domain.IssuingInterfaceProtocol;
import com.staging.sg.card.issuing.domain.IssuingInterfaceType;
import com.staging.sg.card.issuing.service.IssuingEndpointResolver;
import org.springframework.stereotype.Service;

import javax.net.SocketFactory;
import javax.net.ssl.SSLSocketFactory;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Transport d'octets payShield. Cette classe ne construit aucune commande
 * propriétaire : elle transporte une commande produite par un profil HSM
 * validé, sans jamais en journaliser le contenu.
 */
@Service
public class ThalesHostTransport {
    private static final int DEFAULT_MAX_RESPONSE_BYTES = 16_384;

    private final IssuingEndpointResolver endpoints;
    private final ObjectMapper json;

    public ThalesHostTransport(
            IssuingEndpointResolver endpoints, ObjectMapper json) {
        this.endpoints = endpoints;
        this.json = json;
    }

    public byte[] exchange(String issuerId, byte[] command) {
        if (command == null || command.length == 0) {
            throw new IllegalArgumentException("HSM command is required");
        }
        IssuingInterfaceEndpoint endpoint = endpoints.requireActive(
                issuerId, IssuingInterfaceType.HSM);
        TransportParameters parameters = parameters(endpoint);
        if (command.length > parameters.maxMessageBytes()) {
            throw new IllegalArgumentException("HSM command is too large");
        }
        try (Socket socket = socket(endpoint)) {
            socket.connect(
                    new InetSocketAddress(endpoint.host(), endpoint.port()),
                    endpoint.connectTimeoutMs());
            socket.setSoTimeout(endpoint.readTimeoutMs());
            write(socket.getOutputStream(), parameters.framing(), command);
            return read(socket, parameters);
        } catch (IOException transportFailure) {
            throw new IllegalStateException("HSM transport unavailable",
                    transportFailure);
        }
    }

    private Socket socket(IssuingInterfaceEndpoint endpoint)
            throws IOException {
        SocketFactory factory = switch (endpoint.protocol()) {
            case TCP -> SocketFactory.getDefault();
            case TLS_TCP -> SSLSocketFactory.getDefault();
            default -> throw new IllegalStateException(
                    "HSM endpoint must use TCP or TLS_TCP");
        };
        return factory.createSocket();
    }

    private TransportParameters parameters(IssuingInterfaceEndpoint endpoint) {
        try {
            JsonNode root = json.readTree(endpoint.parametersJson());
            Framing framing = Framing.valueOf(
                    root.path("framing").asText("BINARY_2_BE"));
            int maxMessageBytes = root.path("maxMessageBytes")
                    .asInt(DEFAULT_MAX_RESPONSE_BYTES);
            if (maxMessageBytes < 1 || maxMessageBytes > 65_535) {
                throw new IllegalArgumentException(
                        "Invalid HSM maxMessageBytes");
            }
            return new TransportParameters(framing, maxMessageBytes);
        } catch (IOException | IllegalArgumentException invalid) {
            throw new IllegalStateException(
                    "Invalid HSM transport parameters", invalid);
        }
    }

    private static void write(
            OutputStream output, Framing framing, byte[] command)
            throws IOException {
        switch (framing) {
            case BINARY_2_BE -> {
                if (command.length > 65_535) {
                    throw new IllegalArgumentException(
                            "HSM command exceeds binary framing");
                }
                output.write((command.length >>> 8) & 0xff);
                output.write(command.length & 0xff);
            }
            case ASCII_4 -> {
                if (command.length > 9_999) {
                    throw new IllegalArgumentException(
                            "HSM command exceeds ASCII framing");
                }
                output.write("%04d".formatted(command.length)
                        .getBytes(StandardCharsets.US_ASCII));
            }
        }
        output.write(command);
        output.flush();
    }

    private static byte[] read(
            Socket socket, TransportParameters parameters) throws IOException {
        DataInputStream input = new DataInputStream(socket.getInputStream());
        int length = switch (parameters.framing()) {
            case BINARY_2_BE -> input.readUnsignedShort();
            case ASCII_4 -> parseAsciiLength(input.readNBytes(4));
        };
        if (length < 1 || length > parameters.maxMessageBytes()) {
            throw new IOException("Invalid HSM response length");
        }
        byte[] response = input.readNBytes(length);
        if (response.length != length) {
            throw new IOException("Incomplete HSM response");
        }
        return response;
    }

    private static int parseAsciiLength(byte[] value) throws IOException {
        if (value.length != 4) throw new IOException("Missing HSM frame");
        String text = new String(value, StandardCharsets.US_ASCII);
        if (!text.matches("\\d{4}")) {
            throw new IOException("Invalid ASCII HSM frame");
        }
        return Integer.parseInt(text);
    }

    enum Framing {
        BINARY_2_BE,
        ASCII_4
    }

    private record TransportParameters(
            Framing framing, int maxMessageBytes) {
    }
}
