package com.staging.sg.visa.visanet.simulator.service;

import com.staging.sg.visa.common.online.*;
import org.jpos.iso.ISOMsg;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class VisaNetSimulatorService {
    private final String responseCode;
    private final String aci;
    private final Map<String, VisaOnlineNetworkEnvelope> replies = new ConcurrentHashMap<>();
    private final VisaOnlineMessageCodec codec = new VisaOnlineMessageCodec();

    public VisaNetSimulatorService(
            @Value("${visa.simulator.default-response-code:05}") String responseCode,
            @Value("${visa.simulator.aci:Y}") String aci) {
        if (!responseCode.matches("\\d{2}") || !aci.matches("[A-Z]") )
            throw new IllegalArgumentException("Invalid Visa simulator decision configuration");
        this.responseCode = responseCode;
        this.aci = aci;
    }

    public VisaOnlineNetworkEnvelope exchange(VisaOnlineNetworkEnvelope envelope) {
        return replies.computeIfAbsent(envelope.idempotencyKey(), ignored -> createResponse(envelope));
    }

    private VisaOnlineNetworkEnvelope createResponse(VisaOnlineNetworkEnvelope envelope) {
        ISOMsg request = codec.unpack(Base64.getDecoder().decode(envelope.isoMessageBase64()));
        try {
            String responseMti = switch (request.getMTI()) {
                case "0100", "0101" -> "0110";
                case "0400", "0401" -> "0410";
                case "0420", "0421" -> "0430";
                case "0800" -> "0810";
                default -> throw new IllegalArgumentException("Unsupported Visa simulator MTI");
            };
            ISOMsg response = new ISOMsg(responseMti);
            copy(request, response, 2, 3, 4, 7, 11, 12, 13, 18, 19, 22, 25, 32, 37, 41, 42, 49, 60, 70, 90, 126);
            response.set(39, responseCode);
            if ((responseMti.equals("0110") || responseMti.equals("0410") || responseMti.equals("0430"))
                    && responseCode.equals("00")) {
                String seed = envelope.transactionId() + '|' + request.getString(37) + '|' + request.getString(11);
                String tid = numericReference(seed, 15);
                String validation = hex(seed + "|VALIDATION").substring(0, 4);
                response.set(38, numericReference(seed + "|AUTH", 6));
                response.set(62, VisaField62Codec.encode(aci, tid, validation));
            }
            byte[] packed = codec.pack(response);
            return new VisaOnlineNetworkEnvelope("1.0", envelope.transactionId(),
                    envelope.correlationId(), envelope.idempotencyKey(),
                    Base64.getEncoder().encodeToString(packed), "SIMULATED_NETWORK");
        } catch (org.jpos.iso.ISOException e) {
            throw new IllegalArgumentException("Invalid Visa simulator request", e);
        }
    }

    private static void copy(ISOMsg from, ISOMsg to, int... fields) throws org.jpos.iso.ISOException {
        for (int field : fields) if (from.hasField(field)) to.set(field, from.getString(field));
    }

    private static String numericReference(String seed, int length) {
        BigInteger number = new BigInteger(1, digest(seed));
        return String.format("%0" + length + "d", number.mod(BigInteger.TEN.pow(length)));
    }

    private static String hex(String seed) { return java.util.HexFormat.of().formatHex(digest(seed)).toUpperCase(); }
    private static byte[] digest(String seed) {
        try { return MessageDigest.getInstance("SHA-256").digest(seed.getBytes(StandardCharsets.UTF_8)); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
