package com.staging.sg.visa.base2.network.service;

import com.staging.sg.visa.base2.common.*;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class VisaBase2NetworkSimulatorService {
    private final Map<String, VisaBase2NetworkAck> filesByHash = new ConcurrentHashMap<>();

    public VisaBase2NetworkAck receive(VisaBase2NetworkFileEnvelope envelope) {
        byte[] ctf;
        try { ctf = Base64.getDecoder().decode(envelope.ctfBase64()); }
        catch (IllegalArgumentException e) { return rejected(envelope, List.of("Invalid Base64 CTF")); }
        String actualHash = sha256(ctf);
        if (!MessageDigest.isEqual(actualHash.getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                envelope.sha256().getBytes(java.nio.charset.StandardCharsets.US_ASCII))) {
            return rejected(envelope, List.of("CTF checksum mismatch"));
        }
        VisaBase2NetworkAck existing = filesByHash.get(actualHash);
        if (existing != null) return new VisaBase2NetworkAck(existing.fileId(), existing.status(),
                existing.recordCount(), existing.sha256(), true, existing.provenance(), existing.errors());
        VisaBase2FileValidator.ValidationResult validation = new VisaBase2FileValidator().validate(ctf);
        VisaBase2NetworkAck ack = new VisaBase2NetworkAck(envelope.fileId(),
                validation.valid() ? "ACCEPTED" : "REJECTED", validation.recordCount(), actualHash,
                false, "SIMULATED_NETWORK", validation.errors());
        filesByHash.put(actualHash, ack);
        return ack;
    }

    public List<VisaBase2NetworkAck> files() { return List.copyOf(filesByHash.values()); }

    private static VisaBase2NetworkAck rejected(VisaBase2NetworkFileEnvelope envelope, List<String> errors) {
        return new VisaBase2NetworkAck(envelope.fileId(), "REJECTED", 0, envelope.sha256(),
                false, "SIMULATED_NETWORK", errors);
    }

    public static String sha256(byte[] bytes) {
        try { return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
