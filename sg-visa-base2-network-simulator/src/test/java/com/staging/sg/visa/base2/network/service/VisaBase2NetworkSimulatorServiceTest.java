package com.staging.sg.visa.base2.network.service;

import com.staging.sg.visa.base2.common.*;
import org.junit.jupiter.api.Test;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class VisaBase2NetworkSimulatorServiceTest {
    @Test
    void rejectsMalformedFileWithoutFabricatingAcceptance() {
        byte[] invalid = new byte[168];
        String hash = VisaBase2NetworkSimulatorService.sha256(invalid);
        VisaBase2NetworkAck ack = new VisaBase2NetworkSimulatorService().receive(
                new VisaBase2NetworkFileEnvelope("1.0", "F-1", "C-1",
                        Base64.getEncoder().encodeToString(invalid), hash, "SIMULATED_NETWORK"));
        assertThat(ack.status()).isEqualTo("REJECTED");
        assertThat(ack.errors()).isNotEmpty();
    }
}
