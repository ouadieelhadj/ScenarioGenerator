package com.staging.sg.waypos.server.service;

import com.staging.sg.common.routing.RoutingTransactionRequest;
import com.staging.sg.common.routing.RoutingTransactionResponse;
import com.staging.sg.waypos.server.domain.PosAuthorization;
import com.staging.sg.waypos.server.domain.PosCard;
import com.staging.sg.waypos.server.domain.PosHold;
import com.staging.sg.waypos.server.repository.PosAuthorizationRepository;
import com.staging.sg.waypos.server.repository.PosCardRepository;
import com.staging.sg.waypos.server.repository.PosHoldRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Set;
import com.staging.sg.common.iso.WayPosPrivateData;
import com.staging.sg.common.iso.WayPosOperationCatalog.Effect;

@Service
public class Internal00000Service {
    private static final String ROUTE = "00000";
    private static final Set<String> UNCONNECTED_LOCAL_OPERATIONS = Set.of(
            "MINI_STATEMENT",
            "CASH_BY_CODE",
            "LOYALTY_PROGRAM_REQUEST",
            "UTILITY_PAYMENT_AUTHORIZATION",
            "UTILITY_PAYMENT",
            "BILL_PAYMENT_AUTHORIZATION",
            "BILL_PAYMENT_ADVICE",
            "INFORMATION_INQUIRY",
            "AUTHORIZATION_OTHER",
            "FINANCIAL_OTHER");
    private final PosCardRepository cards;
    private final PosAuthorizationRepository authorizations;
    private final PosHoldRepository holds;
    private final PanProtectionService panProtection;
    private final WayPosLocalPinService pinService;
    private final WayPosLocalEmvService emvService;

    public Internal00000Service(
            PosCardRepository cards, PosAuthorizationRepository authorizations,
            PosHoldRepository holds, PanProtectionService panProtection,
            WayPosLocalPinService pinService,
            WayPosLocalEmvService emvService) {
        this.cards = cards;
        this.authorizations = authorizations;
        this.holds = holds;
        this.panProtection = panProtection;
        this.pinService = pinService;
        this.emvService = emvService;
    }

    @Transactional
    public RoutingTransactionResponse process(RoutingTransactionRequest request) {
        Effect effect = Effect.valueOf(request.operation());
        String operationName = attribute(request, "operationName");
        if (UNCONNECTED_LOCAL_OPERATIONS.contains(operationName)) {
            return RoutingTransactionResponse.decline(
                    request.transactionId(), "96", ROUTE);
        }
        if (effect == Effect.REVERSAL) {
            return reverse(request);
        }
        if (effect == Effect.CAPTURE) {
            return capture(request);
        }
        if (effect == Effect.ADVICE) {
            return acknowledgeAdvice(request);
        }
        if ("P2P_CARD_TO_CARD".equals(operationName)) {
            return p2p(request);
        }
        if (effect == Effect.CREDIT
                && request.processingCode() != null
                && request.processingCode().startsWith("21")) {
            return creditTarget(request);
        }
        PosCard card = cards.findLockedByPanHash(panProtection.hash(request.pan())).orElse(null);
        if (card == null) {
            return RoutingTransactionResponse.decline(request.transactionId(), "14", ROUTE);
        }
        if (!card.isActive()) {
            return RoutingTransactionResponse.decline(request.transactionId(), "62", ROUTE);
        }
        if (!card.getExpiryYymm().equals(request.expiry()) || isExpired(card.getExpiryYymm())) {
            return RoutingTransactionResponse.decline(request.transactionId(), "54", ROUTE);
        }
        if (!card.getCurrency().equals(request.currency())) {
            return RoutingTransactionResponse.decline(request.transactionId(), "57", ROUTE);
        }
        long amount = parseAmount(request.amount());
        if ((effect == Effect.HOLD || effect == Effect.DEBIT)
                && card.getAvailableBalance() < amount) {
            return RoutingTransactionResponse.decline(request.transactionId(), "51", ROUTE);
        }
        if (request.pinBlockHex() != null && !request.pinBlockHex().matches("[0-9A-Fa-f]{16}")) {
            return RoutingTransactionResponse.decline(request.transactionId(), "55", ROUTE);
        }
        WayPosLocalPinService.Verification pin = pinService.verify(request, card);
        if (pin == WayPosLocalPinService.Verification.INVALID) {
            return RoutingTransactionResponse.decline(request.transactionId(), "55", ROUTE);
        }
        if (pin == WayPosLocalPinService.Verification.NOT_CONFIGURED
                || pin == WayPosLocalPinService.Verification.ERROR) {
            return RoutingTransactionResponse.decline(request.transactionId(), "96", ROUTE);
        }
        WayPosLocalEmvService.Validation emv = emvService.validate(request, card);
        switch (emv.status()) {
            case INVALID_ARQC, REPLAY -> {
                return RoutingTransactionResponse.decline(
                        request.transactionId(), "05", ROUTE);
            }
            case MALFORMED -> {
                return RoutingTransactionResponse.decline(
                        request.transactionId(), "30", ROUTE);
            }
            case NOT_CONFIGURED, ERROR -> {
                return RoutingTransactionResponse.decline(
                        request.transactionId(), "96", ROUTE);
            }
            default -> { }
        }
        if ("PIN_MANAGEMENT".equals(operationName)) {
            return pinManagement(request, card, pin, emv);
        }
        if ("CARD_CONTROL_REQUEST".equals(operationName)) {
            return cardControl(request, emv);
        }
        switch (effect) {
            case HOLD -> {
                card.reserve(amount);
                holds.save(PosHold.active(request.transactionId(),
                        panProtection.hash(request.pan()), amount));
            }
            case DEBIT -> card.debit(amount);
            case CREDIT -> card.credit(amount);
            case INQUIRY -> {
                return new RoutingTransactionResponse(
                        request.transactionId(), "APPROVED", "00", "00", null,
                        ROUTE, request.amount(),
                        emvService.approvalResponse(card, emv), false,
                        Map.of("processing", "INTERNAL_INQUIRY",
                                "availableBalance", Long.toString(card.getAvailableBalance())));
            }
            default -> {
                return RoutingTransactionResponse.decline(request.transactionId(), "57", ROUTE);
            }
        }
        cards.save(card);
        String authCode = authorizationCode(request);
        return new RoutingTransactionResponse(
                request.transactionId(), "APPROVED", "00", "00", authCode,
                ROUTE, "%012d".formatted(amount),
                emvService.approvalResponse(card, emv), false,
                Map.of("processing", "INTERNAL"));
    }

    private RoutingTransactionResponse capture(RoutingTransactionRequest request) {
        var original = original(request);
        if (original.isEmpty()) {
            return RoutingTransactionResponse.decline(request.transactionId(), "25", ROUTE);
        }
        if ("TIP_PURCHASE_COMPLETION".equals(
                attribute(request, "operationName"))) {
            return completeTip(request, original.get());
        }
        PosHold hold = holds.findLockedByTransactionId(
                original.get().getTransactionId()).orElse(null);
        if (hold == null || !hold.isActive()) {
            return RoutingTransactionResponse.decline(request.transactionId(), "25", ROUTE);
        }
        long amount = parseAmount(request.amount());
        boolean afd = "AFD_COMPLETION".equals(
                attribute(request, "operationName"));
        if ((!afd && amount != hold.getAmountMinor()) || amount <= 0) {
            return RoutingTransactionResponse.decline(request.transactionId(), "13", ROUTE);
        }
        PosCard card = cards.findLockedByPanHash(hold.getPanHash()).orElse(null);
        if (card == null) {
            return RoutingTransactionResponse.decline(request.transactionId(), "14", ROUTE);
        }
        long held = hold.getAmountMinor();
        if (amount > held && card.getAvailableBalance() < amount - held) {
            return RoutingTransactionResponse.decline(
                    request.transactionId(), "51", ROUTE);
        }
        card.capture(held);
        if (amount < held) {
            card.credit(held - amount);
        } else if (amount > held) {
            card.debit(amount - held);
        }
        hold.capture();
        cards.save(card);
        holds.save(hold);
        return approved(request, "CAPTURED");
    }

    private RoutingTransactionResponse acknowledgeAdvice(
            RoutingTransactionRequest request) {
        var original = original(request);
        if (original.isEmpty()) {
            return RoutingTransactionResponse.decline(request.transactionId(), "25", ROUTE);
        }
        return approved(request, "ADVICE_ACKNOWLEDGED");
    }

    private RoutingTransactionResponse p2p(RoutingTransactionRequest request) {
        String targetPan = attribute(request, "targetAccount");
        if (targetPan == null || !targetPan.matches("\\d{12,19}")
                || targetPan.equals(request.pan())) {
            return RoutingTransactionResponse.decline(request.transactionId(), "30", ROUTE);
        }
        CardPair pair = lockPair(request.pan(), targetPan);
        if (pair.source() == null || pair.target() == null) {
            return RoutingTransactionResponse.decline(request.transactionId(), "14", ROUTE);
        }
        String sourceRc = validateSourceCard(pair.source(), request);
        if (sourceRc != null) {
            return RoutingTransactionResponse.decline(request.transactionId(), sourceRc, ROUTE);
        }
        String targetRc = validateTargetCard(pair.target(), request.currency());
        if (targetRc != null) {
            return RoutingTransactionResponse.decline(request.transactionId(), targetRc, ROUTE);
        }
        long amount = parseAmount(request.amount());
        if (pair.source().getAvailableBalance() < amount) {
            return RoutingTransactionResponse.decline(request.transactionId(), "51", ROUTE);
        }
        SecurityCheck security = validateSecurity(request, pair.source());
        if (security.responseCode() != null) {
            return RoutingTransactionResponse.decline(
                    request.transactionId(), security.responseCode(), ROUTE);
        }
        pair.source().debit(amount);
        pair.target().credit(amount);
        cards.save(pair.source());
        cards.save(pair.target());
        return new RoutingTransactionResponse(
                request.transactionId(), "APPROVED", "00", "00",
                authorizationCode(request), ROUTE, "%012d".formatted(amount),
                emvService.approvalResponse(pair.source(), security.emv()), false,
                Map.of("processing", "INTERNAL_P2P"));
    }

    private RoutingTransactionResponse creditTarget(
            RoutingTransactionRequest request) {
        String targetPan = attribute(request, "targetAccount");
        if (targetPan == null || !targetPan.matches("\\d{12,19}")
                || targetPan.equals(request.pan())) {
            return RoutingTransactionResponse.decline(request.transactionId(), "30", ROUTE);
        }
        CardPair pair = lockPair(request.pan(), targetPan);
        if (pair.source() == null || pair.target() == null) {
            return RoutingTransactionResponse.decline(request.transactionId(), "14", ROUTE);
        }
        String sourceRc = validateSourceCard(pair.source(), request);
        if (sourceRc != null) {
            return RoutingTransactionResponse.decline(request.transactionId(), sourceRc, ROUTE);
        }
        String targetRc = validateTargetCard(pair.target(), request.currency());
        if (targetRc != null) {
            return RoutingTransactionResponse.decline(request.transactionId(), targetRc, ROUTE);
        }
        SecurityCheck security = validateSecurity(request, pair.source());
        if (security.responseCode() != null) {
            return RoutingTransactionResponse.decline(
                    request.transactionId(), security.responseCode(), ROUTE);
        }
        long amount = parseAmount(request.amount());
        pair.target().credit(amount);
        cards.save(pair.source());
        cards.save(pair.target());
        return new RoutingTransactionResponse(
                request.transactionId(), "APPROVED", "00", "00",
                authorizationCode(request), ROUTE, "%012d".formatted(amount),
                emvService.approvalResponse(pair.source(), security.emv()), false,
                Map.of("processing", "INTERNAL_CREDIT"));
    }

    private CardPair lockPair(String sourcePan, String targetPan) {
        String sourceHash = panProtection.hash(sourcePan);
        String targetHash = panProtection.hash(targetPan);
        boolean sourceFirst = sourceHash.compareTo(targetHash) <= 0;
        PosCard first = cards.findLockedByPanHash(
                sourceFirst ? sourceHash : targetHash).orElse(null);
        PosCard second = cards.findLockedByPanHash(
                sourceFirst ? targetHash : sourceHash).orElse(null);
        return sourceFirst
                ? new CardPair(first, second) : new CardPair(second, first);
    }

    private String validateSourceCard(
            PosCard card, RoutingTransactionRequest request) {
        if (!card.isActive()) return "62";
        if (!card.getExpiryYymm().equals(request.expiry())
                || isExpired(card.getExpiryYymm())) return "54";
        if (!card.getCurrency().equals(request.currency())) return "57";
        return null;
    }

    private String validateTargetCard(PosCard card, String currency) {
        if (!card.isActive()) return "62";
        if (isExpired(card.getExpiryYymm())) return "54";
        if (!card.getCurrency().equals(currency)) return "57";
        return null;
    }

    private SecurityCheck validateSecurity(
            RoutingTransactionRequest request, PosCard card) {
        if (request.pinBlockHex() != null
                && !request.pinBlockHex().matches("[0-9A-Fa-f]{16}")) {
            return new SecurityCheck("55", null);
        }
        WayPosLocalPinService.Verification pin = pinService.verify(request, card);
        if (pin == WayPosLocalPinService.Verification.INVALID) {
            return new SecurityCheck("55", null);
        }
        if (pin == WayPosLocalPinService.Verification.NOT_CONFIGURED
                || pin == WayPosLocalPinService.Verification.ERROR) {
            return new SecurityCheck("96", null);
        }
        WayPosLocalEmvService.Validation emv = emvService.validate(request, card);
        String rc = switch (emv.status()) {
            case INVALID_ARQC, REPLAY -> "05";
            case MALFORMED -> "30";
            case NOT_CONFIGURED, ERROR -> "96";
            default -> null;
        };
        return new SecurityCheck(rc, emv);
    }

    private RoutingTransactionResponse reverse(RoutingTransactionRequest request) {
        var original = original(request);
        if (original.isEmpty()) {
            return RoutingTransactionResponse.decline(request.transactionId(), "25", ROUTE);
        }
        var transaction = original.get();
        if ("REVERSED".equals(transaction.getStatus())) {
            return approved(request, "ALREADY_REVERSED");
        }
        String panHash = panProtection.hash(request.pan());
        if (!panHash.equals(transaction.getPanHash())) {
            return RoutingTransactionResponse.decline(request.transactionId(), "14", ROUTE);
        }
        long requestedAmount = parseAmount(request.amount());
        long originalAmount = transaction.getAmountMinor() == null ? 0 : transaction.getAmountMinor();
        if (requestedAmount <= 0 || requestedAmount > originalAmount) {
            return RoutingTransactionResponse.decline(request.transactionId(), "13", ROUTE);
        }
        PosCard card = cards.findLockedByPanHash(panHash).orElse(null);
        if (card == null) {
            return RoutingTransactionResponse.decline(request.transactionId(), "14", ROUTE);
        }
        if (transaction.getMti().startsWith("01")) {
            PosHold hold = holds.findLockedByTransactionId(transaction.getTransactionId())
                    .orElse(null);
            if (hold == null || !hold.isActive() || requestedAmount != hold.getAmountMinor()) {
                return RoutingTransactionResponse.decline(request.transactionId(), "25", ROUTE);
            }
            card.release(requestedAmount);
            hold.release();
            holds.save(hold);
        } else {
            card.credit(requestedAmount);
        }
        cards.save(card);
        if ("402".equals(attribute(request, "networkId"))) {
            transaction.markAutomaticallyReversed();
        } else {
            transaction.markReversed();
        }
        authorizations.save(transaction);
        return approved(request, "REVERSED");
    }

    private RoutingTransactionResponse completeTip(
            RoutingTransactionRequest request, PosAuthorization original) {
        if (!"APPROVED".equals(original.getStatus())
                || original.getAmountMinor() == null
                || !original.getMti().startsWith("02")) {
            return RoutingTransactionResponse.decline(
                    request.transactionId(), "25", ROUTE);
        }
        long finalAmount = parseAmount(request.amount());
        long tipAmount;
        String tip = privateValue(attribute(request, "privateData63"), "38");
        try {
            if (tip == null || !tip.matches("\\d{12}")) {
                return RoutingTransactionResponse.decline(
                        request.transactionId(), "30", ROUTE);
            }
            tipAmount = Long.parseLong(tip);
        } catch (NumberFormatException e) {
            return RoutingTransactionResponse.decline(
                    request.transactionId(), "30", ROUTE);
        }
        if (tipAmount <= 0
                || finalAmount != Math.addExact(original.getAmountMinor(), tipAmount)) {
            return RoutingTransactionResponse.decline(
                    request.transactionId(), "13", ROUTE);
        }
        String panHash = panProtection.hash(request.pan());
        if (!panHash.equals(original.getPanHash())) {
            return RoutingTransactionResponse.decline(
                    request.transactionId(), "14", ROUTE);
        }
        PosCard card = cards.findLockedByPanHash(panHash).orElse(null);
        if (card == null) {
            return RoutingTransactionResponse.decline(
                    request.transactionId(), "14", ROUTE);
        }
        if (card.getAvailableBalance() < tipAmount) {
            return RoutingTransactionResponse.decline(
                    request.transactionId(), "51", ROUTE);
        }
        card.debit(tipAmount);
        original.adjustAmount(finalAmount);
        cards.save(card);
        authorizations.save(original);
        return approved(request, "TIP_COMPLETED");
    }

    private java.util.Optional<PosAuthorization> original(
            RoutingTransactionRequest request) {
        return request.originalTransactionId() != null
                ? authorizations.findById(request.originalTransactionId())
                : authorizations
                .findFirstByRrnAndTransactionIdNotOrderByCreatedAtDesc(
                        request.rrn(), request.transactionId());
    }

    private RoutingTransactionResponse approved(
            RoutingTransactionRequest request, String processing) {
        return new RoutingTransactionResponse(
                request.transactionId(), "APPROVED", "00", "00", null,
                ROUTE, request.amount(), null, false, Map.of("processing", processing));
    }

    private RoutingTransactionResponse pinManagement(
            RoutingTransactionRequest request, PosCard card,
            WayPosLocalPinService.Verification oldPin,
            WayPosLocalEmvService.Validation emv) {
        if (request.emvDataHex() != null) {
            // EMV PIN change/enrolment needs issuer scripts, not only ARPC.
            return RoutingTransactionResponse.decline(
                    request.transactionId(), "96", ROUTE);
        }
        boolean change = request.pinBlockHex() != null;
        if (change && oldPin != WayPosLocalPinService.Verification.VERIFIED) {
            return RoutingTransactionResponse.decline(
                    request.transactionId(), "55", ROUTE);
        }
        if (!change && card.getPinPvv() != null) {
            return RoutingTransactionResponse.decline(
                    request.transactionId(), "57", ROUTE);
        }
        String newPinBlock = privateValue(
                attribute(request, "securityAdditionalData"), "11");
        String pc = privateValue(attribute(request, "privateData63"), "PC");
        if (pc == null || pc.length() < 3 || pc.charAt(2) != '0') {
            return RoutingTransactionResponse.decline(
                    request.transactionId(), "30", ROUTE);
        }
        WayPosLocalPinService.PinUpdate update =
                pinService.updatePvv(request, card, newPinBlock);
        if (update == WayPosLocalPinService.PinUpdate.INVALID_BLOCK) {
            return RoutingTransactionResponse.decline(
                    request.transactionId(), "30", ROUTE);
        }
        if (update != WayPosLocalPinService.PinUpdate.UPDATED) {
            return RoutingTransactionResponse.decline(
                    request.transactionId(), "96", ROUTE);
        }
        cards.save(card);
        return new RoutingTransactionResponse(
                request.transactionId(), "APPROVED", "00", "00",
                authorizationCode(request), ROUTE, request.amount(),
                emvService.approvalResponse(card, emv), false,
                Map.of("processing", change ? "PIN_CHANGED" : "PIN_ENROLLED"));
    }

    private RoutingTransactionResponse cardControl(
            RoutingTransactionRequest request,
            WayPosLocalEmvService.Validation emv) {
        String inquiryType = privateValue(
                attribute(request, "privateData63"), "62");
        if (inquiryType == null) {
            return RoutingTransactionResponse.decline(
                    request.transactionId(), "30", ROUTE);
        }
        if (emv.status() != WayPosLocalEmvService.Status.VERIFIED) {
            return RoutingTransactionResponse.decline(
                    request.transactionId(), "30", ROUTE);
        }
        // These actions modify the chip and require real issuer scripts.
        // Until the issuer-script key/service is connected, never approve
        // them by changing only the server-side card record.
        return new RoutingTransactionResponse(
                request.transactionId(), "DECLINED", "96", "96",
                null, ROUTE, null, null, false,
                Map.of("processing", "ISSUER_SCRIPT_REQUIRED",
                        "inquiryType", inquiryType));
    }


    private static boolean isExpired(String yymm) {
        try {
            YearMonth expiry = YearMonth.parse(yymm, DateTimeFormatter.ofPattern("yyMM"));
            return expiry.isBefore(YearMonth.now());
        } catch (RuntimeException e) {
            return true;
        }
    }

    private static long parseAmount(String value) {
        if (value == null || !value.matches("\\d{1,12}")) {
            throw new IllegalArgumentException("Invalid amount");
        }
        return Long.parseLong(value);
    }

    private static String attribute(
            RoutingTransactionRequest request, String name) {
        return request.attributes() == null ? null : request.attributes().get(name);
    }

    private static String authorizationCode(RoutingTransactionRequest request) {
        return "%06d".formatted(
                Math.floorMod(request.transactionId().hashCode(), 1_000_000));
    }

    private static String privateValue(String data, String tableId) {
        if (data == null) return null;
        try {
            return WayPosPrivateData.decode(data).stream()
                    .filter(item -> tableId.equals(item.tableId()))
                    .map(WayPosPrivateData.Item::value)
                    .findFirst().orElse(null);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private record CardPair(PosCard source, PosCard target) {}
    private record SecurityCheck(
            String responseCode, WayPosLocalEmvService.Validation emv) {}
}
