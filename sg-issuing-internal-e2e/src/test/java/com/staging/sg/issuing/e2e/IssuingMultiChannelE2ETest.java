package com.staging.sg.issuing.e2e;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.staging.sg.card.issuing.domain.CardContract;
import com.staging.sg.card.issuing.domain.CardInstrument;
import com.staging.sg.card.issuing.domain.CardProduct;
import com.staging.sg.card.issuing.domain.CardType;
import com.staging.sg.card.issuing.domain.IssuingAuthorization;
import com.staging.sg.card.issuing.domain.PaymentIdentifier;
import com.staging.sg.card.issuing.domain.PaymentIdentifierStatus;
import com.staging.sg.card.issuing.port.CardSecurityPort;
import com.staging.sg.card.issuing.port.FundingAuthorizationPort;
import com.staging.sg.card.issuing.repository.CardContractRepository;
import com.staging.sg.card.issuing.repository.CardInstrumentRepository;
import com.staging.sg.card.issuing.repository.CardProductRepository;
import com.staging.sg.card.issuing.repository.PaymentIdentifierRepository;
import com.staging.sg.card.issuing.service.AuthorizationJournalService;
import com.staging.sg.card.issuing.service.DatabasePaymentIdentifierResolver;
import com.staging.sg.card.issuing.service.IssuerDecisionService;
import com.staging.sg.common.entity.SwamInterface;
import com.staging.sg.common.emv.McDmasEmv;
import com.staging.sg.common.iso.WayPosBerTlv;
import com.staging.sg.common.iso.crypto.JposHsmService;
import com.staging.sg.common.issuing.IssuingAuthorizationRequest;
import com.staging.sg.common.issuing.IssuingAuthorizationResponse;
import com.staging.sg.common.issuing.client.DatabaseIssuingClient;
import com.staging.sg.common.issuing.client.IssuingEndpoint;
import com.staging.sg.common.issuing.client.IssuingEndpointDirectory;
import com.staging.sg.common.routing.RoutingTransactionRequest;
import com.staging.sg.common.service.McDmasInterfaceService;
import com.staging.sg.common.service.SwamInterfaceService;
import com.staging.sg.mc.dmas.mastercard.network.DmasIssuingAdapter;
import com.staging.sg.swam.issuer.network.SwamIssuingAdapter;
import com.staging.sg.waypos.server.service.Issuing00000Connector;
import com.staging.sg.waypos.server.domain.PosCard;
import com.staging.sg.waypos.server.service.WayPosLocalEmvService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.jpos.iso.ISOMsg;
import org.jpos.iso.ISOUtil;
import org.jpos.security.SecureDESKey;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IssuingMultiChannelE2ETest {
    private static final String ISSUER = "BANK1";
    private static final String PAN = "5321962145453348";

    @TempDir
    Path temporaryDirectory;

    @Test
    void authorizesThroughServerPosSwamAndDmasOverRealJsonRest()
            throws Exception {
        try (InternalIssuingRuntime runtime = new InternalIssuingRuntime()) {
            DatabaseIssuingClient client = runtime.client();

            var serverPos = new Issuing00000Connector(client)
                    .process(serverPosRequest());
            var serverPosRepeat = new Issuing00000Connector(client)
                    .process(serverPosRequest());
            var swam = new SwamIssuingAdapter(
                    client, swamInterfaces()).authorize(swamMessage());
            var dmas = new DmasIssuingAdapter(
                    client, dmasInterfaces()).authorize(dmasMessage());

            assertThat(serverPos.status()).isEqualTo("APPROVED");
            assertThat(serverPos.posResponseCode()).isEqualTo("00");
            assertThat(serverPos.route()).isEqualTo("00000");
            assertThat(serverPosRepeat.authorizationCode())
                    .isEqualTo(serverPos.authorizationCode());
            assertThat(serverPosRepeat.attributes())
                    .containsEntry("replayed", "true");
            assertThat(swam.status()).isEqualTo("APPROVED");
            assertThat(swam.responseCode()).isEqualTo("000");
            assertThat(dmas.status()).isEqualTo("APPROVED");
            assertThat(dmas.responseCode()).isEqualTo("00");
            assertThat(runtime.httpCalls()).isEqualTo(4);
            assertThat(runtime.remainingBalance()).isEqualTo(7_000);
        }
    }

    @Test
    void reusesCvn10EngineForArqcAntiReplayAndArpcTag91()
            throws Exception {
        JposHsmService hsm = new JposHsmService();
        ReflectionTestUtils.setField(
                hsm, "lmkFile",
                temporaryDirectory.resolve("issuing-e2e.lmk").toString());
        ReflectionTestUtils.setField(hsm, "lmkRebuild", true);
        hsm.init();

        byte[] clearMdk = new byte[16];
        new SecureRandom().nextBytes(clearMdk);
        String clearMdkHex = ISOUtil.hexString(clearMdk);
        SecureDESKey protectedMdk = hsm.formClearKey("MDK", clearMdkHex);
        String mdkUnderLmk = ISOUtil.hexString(protectedMdk.getKeyBytes());
        String mdkKcv = hsm.computeKcv(clearMdk);
        java.util.Arrays.fill(clearMdk, (byte) 0);

        McDmasEmv emv = new McDmasEmv(hsm);
        McDmasEmv.EmvInput input = new McDmasEmv.EmvInput();
        input.mdkUnderLmk = mdkUnderLmk;
        input.mdkKcv = mdkKcv;
        input.mdkLenBytes = 16;
        input.pan = PAN;
        input.psn = "01";
        input.atc = 1;
        input.aid = "A0000000041010";
        input.aip = "1800";
        input.iad = "06010A03A00000000000000000000000";
        input.appVersion = "0002";
        input.cvmResults = "420300";
        input.amount = "000000001000";
        input.currency = "0504";
        input.countryCode = "0504";
        input.date = "260731";
        input.unpredictable = "01020304";
        McDmasEmv.EmvResult cardCryptogram = emv.build(input);

        PosCard card = PosCard.provisioned(
                "hash", "532196******3348", "2912", "504", 10_000,
                null, null, mdkUnderLmk, mdkKcv, 16, "01", "3030");
        RoutingTransactionRequest request = new RoutingTransactionRequest(
                "1.0", "emv-tx-1", "emv-corr-1", "emv-idem-1",
                "DEBIT", "0200", "000000", PAN, "2912",
                "000000001000", "504", "400001", "EMVREF000001",
                "TERM0001", "MERCHANT0000001", null,
                cardCryptogram.de55Hex, null, Map.of());
        WayPosLocalEmvService localEmv = new WayPosLocalEmvService(emv);

        WayPosLocalEmvService.Validation verified =
                localEmv.validate(request, card);
        String responseDe55 = localEmv.approvalResponse(card, verified);
        WayPosLocalEmvService.Validation replay =
                localEmv.validate(request, card);

        assertThat(verified.status())
                .isEqualTo(WayPosLocalEmvService.Status.VERIFIED);
        assertThat(card.getLastAtc()).isEqualTo(1);
        var tag91 = WayPosBerTlv.decode(
                ISOUtil.hex2byte(responseDe55)).getFirst();
        assertThat(tag91.tag()).isEqualTo(0x91);
        assertThat(tag91.value()).hasSize(10);
        assertThat(replay.status())
                .isEqualTo(WayPosLocalEmvService.Status.REPLAY);
    }

    private static RoutingTransactionRequest serverPosRequest() {
        return new RoutingTransactionRequest(
                "1.0", "pos-tx-1", "pos-corr-1", "pos-idem-1",
                "DEBIT", "0200", "000000", PAN, "2912",
                "000000001000", "504", "100001", "POSREF000001",
                "TERM0001", "MERCHANT0000001", null, null, null,
                Map.of("issuerId", ISSUER, "cardPresent", "true"));
    }

    private static ISOMsg swamMessage() throws Exception {
        return message("1200", "0731110001", "200001", "SWAMREF00001");
    }

    private static ISOMsg dmasMessage() throws Exception {
        return message("0100", "0731110002", "300001", "DMASREF00001");
    }

    private static ISOMsg message(
            String mti, String transmission, String stan, String reference)
            throws Exception {
        ISOMsg value = new ISOMsg(mti);
        value.set(2, PAN);
        value.set(3, "000000");
        value.set(4, "000000001000");
        value.set(7, transmission);
        value.set(11, stan);
        value.set(12, "260731110000");
        value.set(18, "5411");
        value.set(19, "504");
        value.set(37, reference);
        value.set(41, "TERM0001");
        value.set(42, "MERCHANT0000001");
        value.set(49, "504");
        return value;
    }

    private static SwamInterfaceService swamInterfaces() {
        SwamInterface configured = new SwamInterface();
        configured.setBankCode(ISSUER);
        SwamInterfaceService service = mock(SwamInterfaceService.class);
        when(service.get()).thenReturn(configured);
        return service;
    }

    private static McDmasInterfaceService dmasInterfaces() {
        McDmasInterfaceService service = mock(McDmasInterfaceService.class);
        when(service.bankCode()).thenReturn(ISSUER);
        return service;
    }

    private static final class InternalIssuingRuntime implements AutoCloseable {
        private final ObjectMapper json = new ObjectMapper();
        private final AtomicLong balance = new AtomicLong(10_000);
        private final AtomicInteger httpCalls = new AtomicInteger();
        private final HttpServer server;
        private final DatabaseIssuingClient client;

        private InternalIssuingRuntime() throws IOException {
            IssuerDecisionService decision = decisionEngine();
            server = HttpServer.create(
                    new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext(
                    "/api/issuing/v1/authorizations",
                    exchange -> authorize(exchange, decision));
            server.start();

            IssuingEndpointDirectory directory =
                    mock(IssuingEndpointDirectory.class);
            when(directory.requireActive(anyString(), anyString()))
                    .thenAnswer(invocation -> new IssuingEndpoint(
                            ISSUER, invocation.getArgument(0), "REST",
                            "127.0.0.1", server.getAddress().getPort(),
                            "/api/issuing/v1", 1_000, 2_000));
            client = new DatabaseIssuingClient(
                    directory, RestClient.builder());
        }

        private IssuerDecisionService decisionEngine() {
            CardProduct product = CardProduct.draft(
                    ISSUER, "INTERNAL-E2E", 1, CardType.DEBIT,
                    "504", true, true, false,
                    "maker", "product-idem", "product-fingerprint");
            product.approve("checker");
            product.activate();

            CardContract contract = CardContract.draft(
                    ISSUER, "E2E-CONTRACT", "CUSTOMER-1", "HOLDER-1",
                    "FUNDING-1", product.id(), "maker",
                    "contract-idem", "contract-fingerprint");
            contract.submit();
            contract.approve("checker");

            String token = "pan_tok_internal_e2e";
            CardInstrument instrument = CardInstrument.inactive(
                    ISSUER, contract.id(), token, "532196******3348",
                    "2912", "e2e", "instrument-idem",
                    "instrument-fingerprint");
            instrument.activate(contract.status());
            PaymentIdentifier identifier = PaymentIdentifier.activePan(
                    ISSUER, instrument.id(), token, PAN,
                    "532196******3348");

            PaymentIdentifierRepository identifiers =
                    mock(PaymentIdentifierRepository.class);
            when(identifiers.findByIssuerIdAndPanClearAndStatus(
                    ISSUER, PAN, PaymentIdentifierStatus.ACTIVE))
                    .thenReturn(Optional.of(identifier));
            when(identifiers.findByIssuerIdAndVaultReferenceAndStatus(
                    ISSUER, token, PaymentIdentifierStatus.ACTIVE))
                    .thenReturn(Optional.of(identifier));

            CardInstrumentRepository instruments =
                    mock(CardInstrumentRepository.class);
            when(instruments.findById(instrument.id()))
                    .thenReturn(Optional.of(instrument));
            CardContractRepository contracts =
                    mock(CardContractRepository.class);
            when(contracts.findById(contract.id()))
                    .thenReturn(Optional.of(contract));
            CardProductRepository products =
                    mock(CardProductRepository.class);
            when(products.findById(product.id()))
                    .thenReturn(Optional.of(product));

            AuthorizationJournalService journal =
                    mock(AuthorizationJournalService.class);
            Map<String, IssuingAuthorization> recorded =
                    new ConcurrentHashMap<>();
            when(journal.replay(
                    anyString(), anyString(), anyString(), anyString()))
                    .thenAnswer(invocation -> {
                        String key = journalKey(
                                invocation.getArgument(0),
                                invocation.getArgument(1),
                                invocation.getArgument(2));
                        IssuingAuthorization existing = recorded.get(key);
                        if (existing != null && !existing.requestMatches(
                                invocation.getArgument(3))) {
                            throw new IllegalStateException(
                                    "Idempotency key reused with another payload");
                        }
                        return Optional.ofNullable(existing);
                    });
            when(journal.record(
                    any(IssuingAuthorization.class), anyString()))
                    .thenAnswer(invocation -> {
                        IssuingAuthorization authorization =
                                invocation.getArgument(0);
                        recorded.put(journalKey(
                                authorization.issuerId(),
                                authorization.callerId(),
                                authorization.idempotencyKey()),
                                authorization);
                        return authorization;
                    });
            CardSecurityPort security = command ->
                    new CardSecurityPort.SecurityResult(
                            CardSecurityPort.SecurityStatus.VERIFIED,
                            "VERIFIED", null);
            FundingAuthorizationPort funding = command -> {
                long before = balance.getAndAdd(-command.amountMinor());
                if (before < command.amountMinor()) {
                    balance.addAndGet(command.amountMinor());
                    return new FundingAuthorizationPort.FundingResult(
                            FundingAuthorizationPort.FundingStatus.DECLINED,
                            "INSUFFICIENT_FUNDS", 0, null);
                }
                return new FundingAuthorizationPort.FundingResult(
                        FundingAuthorizationPort.FundingStatus.APPROVED,
                        "APPROVED", command.amountMinor(),
                        "INTERNAL-" + command.transactionId());
            };
            return new IssuerDecisionService(
                    new DatabasePaymentIdentifierResolver(identifiers),
                    identifiers, instruments, contracts, products,
                    security, funding, journal);
        }

        private void authorize(
                HttpExchange exchange, IssuerDecisionService decision)
                throws IOException {
            try {
                httpCalls.incrementAndGet();
                IssuingAuthorizationRequest request = json.readValue(
                        exchange.getRequestBody(),
                        IssuingAuthorizationRequest.class);
                if (!request.idempotencyKey().equals(exchange
                        .getRequestHeaders().getFirst("Idempotency-Key"))
                        || !request.correlationId().equals(exchange
                        .getRequestHeaders().getFirst("X-Correlation-ID"))) {
                    exchange.sendResponseHeaders(400, -1);
                    return;
                }
                IssuingAuthorizationResponse response =
                        decision.authorize(request);
                byte[] body = json.writeValueAsBytes(response);
                exchange.getResponseHeaders().set(
                        "Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
            } catch (RuntimeException failure) {
                exchange.sendResponseHeaders(500, -1);
            } finally {
                exchange.close();
            }
        }

        private DatabaseIssuingClient client() {
            return client;
        }

        private static String journalKey(
                String issuerId, String callerId, String idempotencyKey) {
            return issuerId + "\u001f" + callerId + "\u001f" + idempotencyKey;
        }

        private int httpCalls() {
            return httpCalls.get();
        }

        private long remainingBalance() {
            return balance.get();
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }
}
