package com.staging.sg.visa.base2.member.service;

import com.staging.sg.visa.base2.common.*;
import com.staging.sg.visa.base2.member.api.VisaBase2PresentmentRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class VisaBase2MemberService {
    private final VisaBase2NetworkPort network;
    private final String cib, acquiringIdentifier, businessId;
    private final AtomicLong sequence = new AtomicLong(1);
    private final Map<String, VisaBase2MemberFileView> files = new ConcurrentHashMap<>();

    public VisaBase2MemberService(VisaBase2NetworkPort network,
            @Value("${visa.base2.member.cib:}") String cib,
            @Value("${visa.base2.member.acquiring-identifier:}") String acquiringIdentifier,
            @Value("${visa.base2.member.business-id:}") String businessId) {
        this.network = network; this.cib = cib; this.acquiringIdentifier = acquiringIdentifier; this.businessId = businessId;
    }

    public VisaBase2MemberFileView present(VisaBase2PresentmentRequest request) {
        validate(request);
        VisaBase2MemberFileView existing = files.get(request.transactionId());
        if (existing != null) return new VisaBase2MemberFileView(existing.fileId(), existing.transactionId(),
                existing.correlationId(), existing.arn(), existing.status(), existing.recordCount(),
                existing.sha256(), existing.networkStatus(), true, existing.createdAt(), existing.errors());
        LocalDate date = LocalDate.now(ZoneOffset.UTC); long current = sequence.getAndIncrement();
        String arn = VisaBase2Arn.generate(requiredDigits(acquiringIdentifier, 6, "acquiring identifier"), date, current);
        VisaBase2PresentmentData data = new VisaBase2PresentmentData(request.transactionId(), request.pan(), arn,
                required(businessId, "business ID"), request.purchaseDateMmdd(), request.amountMinor(), request.currency(),
                request.amountMinor(), request.currency(), request.merchantName(), request.merchantCity(),
                request.merchantCountry(), request.mcc(), request.merchantZip(), request.merchantState(),
                request.aci(), request.authorizationCode(), request.posEntryMode(), request.visaTransactionId(),
                request.amountMinor(), request.currency(), request.authorizationResponseCode(), request.validationCode());
        List<VisaBase2Record> records = new VisaBase2FileFactory().purchaseCtf(data,
                requiredDigits(cib, 6, "CIB"), date, (int) (current % 1000), current % 999999 + 1);
        byte[] ctf = new VisaBase2FileCodec().pack(records);
        VisaBase2FileValidator.ValidationResult validation = new VisaBase2FileValidator().validate(ctf);
        if (!validation.valid()) throw new IllegalStateException("Generated Base II file is invalid: " + validation.errors());
        String fileId = "VISA-B2-%06d".formatted(current); String hash = sha256(ctf);
        VisaBase2NetworkAck ack = network.send(new VisaBase2NetworkFileEnvelope("1.0", fileId,
                request.correlationId(), Base64.getEncoder().encodeToString(ctf), hash, "SIMULATED_NETWORK"));
        VisaBase2MemberFileView result = new VisaBase2MemberFileView(fileId, request.transactionId(),
                request.correlationId(), arn, "READY_TO_SEND", validation.recordCount(), hash,
                ack.status(), false, Instant.now(), ack.errors());
        files.put(request.transactionId(), result); return result;
    }

    public List<VisaBase2MemberFileView> files() { return files.values().stream().toList(); }

    private static void validate(VisaBase2PresentmentRequest r) {
        if (r == null || !"1.0".equals(r.schemaVersion()) || r.transactionId() == null
                || r.correlationId() == null || r.pan() == null || !r.pan().matches("\\d{12,19}")
                || r.amountMinor() <= 0 || r.currency() == null || !r.currency().matches("\\d{3}")
                || r.visaTransactionId() == null || !r.visaTransactionId().matches("\\d{15}")
                || r.validationCode() == null || !r.validationCode().matches("[A-Z0-9]{4}"))
            throw new IllegalArgumentException("Invalid Base II presentment request");
    }
    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalStateException("Missing official Visa Base II " + name);
        return value;
    }
    private static String requiredDigits(String value, int length, String name) {
        value = required(value, name); if (!value.matches("\\d{" + length + "}"))
            throw new IllegalStateException("Visa Base II " + name + " must contain " + length + " digits");
        return value;
    }
    private static String sha256(byte[] bytes) {
        try { return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
