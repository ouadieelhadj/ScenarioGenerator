package com.staging.sg.visa.online.member.service;

import com.staging.sg.common.routing.*;
import com.staging.sg.visa.common.online.*;
import org.junit.jupiter.api.Test;
import java.util.Base64;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class VisaOnlineMemberServiceTest {
    @Test
    void authorizesThroughIndependentSimulatorAndKeepsOnlyMaskedPanInJournal() {
        VisaOnlineMessageCodec codec = new VisaOnlineMessageCodec();
        VisaNetTransport simulator = envelope -> {
            try {
                var response = codec.unpack(Base64.getDecoder().decode(envelope.isoMessageBase64()));
                response.setMTI("0110"); response.set(38, "ABC123"); response.set(39, "00");
                response.set(62, VisaField62Codec.encode(
                        "Y", "123456789012345", "A1B2"));
                return new VisaOnlineNetworkEnvelope("1.0", envelope.transactionId(),
                        envelope.correlationId(), envelope.idempotencyKey(),
                        Base64.getEncoder().encodeToString(codec.pack(response)), "SIMULATED_NETWORK");
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        };
        VisaOnlineMemberService service = new VisaOnlineMemberService(simulator,
                "123456", "504", "5999", "MERCHANT TEST CASABLANCA MA");
        RoutingTransactionRequest request = new RoutingTransactionRequest("1.0", "VISA-TX-1",
                "VISA-CORR-1", "VISA-IDEM-1", "AUTHORIZATION", "0100", "000000",
                "4111111111111111", "2912", "000000001000", "504", "000001",
                "621512000001", "ECOM0001", "MID000000000001", null, null, null,
                Map.of("cardProgram", "VISA", "eci", "05"));

        RoutingTransactionResponse first = service.authorize(request);
        RoutingTransactionResponse replay = service.authorize(request);

        assertThat(first.status()).isEqualTo("APPROVED");
        assertThat(first.attributes()).containsKeys("aci", "visaTransactionId", "validationCode");
        assertThat(replay).isSameAs(first);
        assertThat(service.journal()).singleElement().satisfies(entry -> {
            assertThat(entry.maskedPan()).isEqualTo("411111******1111");
            assertThat(entry.toString()).doesNotContain("4111111111111111");
        });
    }
}
