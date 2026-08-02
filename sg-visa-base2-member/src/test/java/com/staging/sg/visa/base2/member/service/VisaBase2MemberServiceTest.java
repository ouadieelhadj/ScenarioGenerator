package com.staging.sg.visa.base2.member.service;

import com.staging.sg.visa.base2.common.*;
import com.staging.sg.visa.base2.member.api.VisaBase2PresentmentRequest;
import org.junit.jupiter.api.Test;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class VisaBase2MemberServiceTest {
    @Test
    void buildsSendsAndDeduplicatesFirstPresentment() {
        VisaBase2NetworkPort network = envelope -> new VisaBase2NetworkAck(envelope.fileId(),
                "ACCEPTED", 5, envelope.sha256(), false, "SIMULATED_NETWORK", List.of());
        VisaBase2MemberService service = new VisaBase2MemberService(network,
                "123456", "123456", "12345678");
        VisaBase2PresentmentRequest request = new VisaBase2PresentmentRequest("1.0", "TX-1", "C-1",
                "4111111111111111", "0802", 1000, "504", "MERCHANT TEST", "CASABLANCA",
                "MAR", "5999", "20000", "CAS", "10", "Y", "ABC123",
                "123456789012345", "00", "A1B2");

        VisaBase2MemberFileView first = service.present(request);
        VisaBase2MemberFileView replay = service.present(request);

        assertThat(first.networkStatus()).isEqualTo("ACCEPTED");
        assertThat(first.recordCount()).isEqualTo(5);
        assertThat(first.arn()).hasSize(23);
        assertThat(replay.replayed()).isTrue();
        assertThat(replay.fileId()).isEqualTo(first.fileId());
    }
}
