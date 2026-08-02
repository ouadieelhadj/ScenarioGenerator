package com.staging.sg.visa.online.member.service;

import com.staging.sg.common.routing.*;
import com.staging.sg.visa.common.online.*;
import org.jpos.iso.ISOMsg;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class VisaOnlineMemberService {
    private final VisaNetTransport transport;
    private final String acquirerId;
    private final String acquirerCountry;
    private final String defaultMcc;
    private final String merchantLocation;
    private final VisaOnlineMessageCodec codec = new VisaOnlineMessageCodec();
    private final Map<String, RoutingTransactionResponse> responses = new ConcurrentHashMap<>();
    private final Map<String, VisaOnlineJournalView> journal = new ConcurrentHashMap<>();

    public VisaOnlineMemberService(VisaNetTransport transport,
            @Value("${visa.online.member.acquirer-id:}") String acquirerId,
            @Value("${visa.online.member.acquirer-country:}") String acquirerCountry,
            @Value("${visa.online.member.default-mcc:}") String defaultMcc,
            @Value("${visa.online.member.merchant-location:}") String merchantLocation) {
        this.transport = transport; this.acquirerId = acquirerId;
        this.acquirerCountry = acquirerCountry; this.defaultMcc = defaultMcc;
        this.merchantLocation = merchantLocation;
    }

    public RoutingTransactionResponse authorize(RoutingTransactionRequest request) {
        validate(request);
        return responses.computeIfAbsent(request.idempotencyKey(), ignored -> exchange(request));
    }

    public List<VisaOnlineJournalView> journal() {
        return journal.values().stream().sorted(Comparator.comparing(VisaOnlineJournalView::createdAt).reversed()).toList();
    }

    public Optional<VisaOnlineJournalView> find(String transactionId) { return Optional.ofNullable(journal.get(transactionId)); }

    private RoutingTransactionResponse exchange(RoutingTransactionRequest request) {
        try {
            ISOMsg message = build(request);
            byte[] packed = codec.pack(message);
            VisaOnlineNetworkEnvelope envelope = new VisaOnlineNetworkEnvelope("1.0",
                    request.transactionId(), request.correlationId(), request.idempotencyKey(),
                    Base64.getEncoder().encodeToString(packed), "SIMULATED_NETWORK");
            VisaOnlineNetworkEnvelope networkReply = transport.exchange(envelope);
            ISOMsg reply = codec.unpack(Base64.getDecoder().decode(networkReply.isoMessageBase64()));
            String rc = reply.getString(39);
            VisaOnlineReferences references = reply.hasField(62)
                    ? VisaField62Codec.decode(reply.getString(62)) : null;
            Map<String, String> attributes = new HashMap<>();
            attributes.put("provenance", "SIMULATED_NETWORK");
            if (references != null) {
                attributes.put("aci", references.aci());
                attributes.put("visaTransactionId", references.transactionId());
                attributes.put("validationCode", references.validationCode());
            }
            String status = "00".equals(rc) ? "APPROVED" : "DECLINED";
            RoutingTransactionResponse response = new RoutingTransactionResponse(request.transactionId(),
                    status, rc, rc, reply.hasField(38) ? reply.getString(38) : null,
                    "VISA_ONLINE_MEMBER", "00".equals(rc) ? request.amount() : null,
                    null, false, Map.copyOf(attributes));
            journal.put(request.transactionId(), journal(request, message, reply, references));
            return response;
        } catch (Exception e) {
            throw new IllegalStateException("Visa Online authorization failed", e);
        }
    }

    private ISOMsg build(RoutingTransactionRequest request) throws org.jpos.iso.ISOException {
        Instant now = Instant.now();
        ZonedDateTime utc = now.atZone(ZoneOffset.UTC);
        ISOMsg message = new ISOMsg("0100");
        message.set(2, request.pan()); message.set(3, request.processingCode());
        message.set(4, request.amount()); message.set(7, utc.format(DateTimeFormatter.ofPattern("MMddHHmmss")));
        message.set(11, request.stan()); message.set(12, utc.format(DateTimeFormatter.ofPattern("HHmmss")));
        message.set(13, utc.format(DateTimeFormatter.ofPattern("MMdd"))); message.set(14, request.expiry());
        message.set(18, required(attribute(request, "mcc"), defaultMcc, "MCC"));
        message.set(19, required(attribute(request, "acquirerCountry"), acquirerCountry, "acquirer country"));
        message.set(22, attribute(request, "entryMode", "010"));
        message.set(25, attribute(request, "conditionCode", "59"));
        message.set(32, required(attribute(request, "acquirerId"), acquirerId, "acquirer identifier"));
        message.set(37, request.rrn()); message.set(41, fixed(request.terminalId(), 8, "terminal"));
        message.set(42, fixed(request.merchantId(), 15, "merchant"));
        message.set(43, fixed(required(attribute(request, "merchantLocation"), merchantLocation,
                "merchant location"), 40, "merchant location"));
        message.set(49, request.currency());
        String eci = attribute(request, "eci");
        message.set(60, eci == null ? "ECI=07" : "ECI=" + eci);
        String authenticationValue = attribute(request, "authenticationValue");
        if (authenticationValue != null) message.set(126, "CAVV=" + authenticationValue);
        return message;
    }

    private VisaOnlineJournalView journal(RoutingTransactionRequest request, ISOMsg sent,
            ISOMsg reply, VisaOnlineReferences refs) throws org.jpos.iso.ISOException {
        Map<String, String> clearing = new LinkedHashMap<>();
        clearing.put("purchaseDate", sent.getString(13)); clearing.put("mcc", sent.getString(18));
        clearing.put("acquirerId", sent.getString(32)); clearing.put("merchantId", sent.getString(42));
        clearing.put("merchantLocation", sent.getString(43).trim());
        return new VisaOnlineJournalView(request.transactionId(), request.correlationId(),
                request.idempotencyKey(), VisaDataMasking.pan(request.pan()), sent.getMTI(), reply.getMTI(),
                request.stan(), request.rrn(), reply.getString(39), reply.hasField(38) ? reply.getString(38) : null,
                Long.parseLong(request.amount()), request.currency(), refs == null ? null : refs.aci(),
                refs == null ? null : refs.transactionId(), refs == null ? null : refs.validationCode(),
                "SIMULATED_NETWORK", Instant.now(), Map.copyOf(clearing));
    }

    private void validate(RoutingTransactionRequest r) {
        if (r == null || !"1.0".equals(r.schemaVersion()) || !"AUTHORIZATION".equals(r.operation())
                || r.pan() == null || !r.pan().matches("\\d{12,19}") || r.amount() == null
                || !r.amount().matches("\\d{12}") || r.currency() == null || !r.currency().matches("\\d{3}")
                || r.stan() == null || !r.stan().matches("\\d{6}") || r.rrn() == null
                || !r.rrn().matches("[A-Z0-9]{12}") || !"VISA".equals(attribute(r, "cardProgram")))
            throw new IllegalArgumentException("Invalid Visa Online routing request");
    }

    private static String attribute(RoutingTransactionRequest r, String key) {
        return r.attributes() == null ? null : r.attributes().get(key);
    }
    private static String attribute(RoutingTransactionRequest r, String key, String fallback) {
        String value = attribute(r, key); return value == null || value.isBlank() ? fallback : value;
    }
    private static String required(String first, String fallback, String name) {
        String value = first == null || first.isBlank() ? fallback : first;
        if (value == null || value.isBlank()) throw new IllegalStateException("Missing Visa " + name);
        return value;
    }
    private static String fixed(String value, int length, String name) {
        if (value == null || value.isBlank() || value.length() > length)
            throw new IllegalArgumentException("Visa " + name + " must contain at most " + length + " characters");
        return value + " ".repeat(length - value.length());
    }
}
