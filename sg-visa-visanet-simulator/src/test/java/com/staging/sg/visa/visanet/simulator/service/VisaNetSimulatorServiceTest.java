package com.staging.sg.visa.visanet.simulator.service;

import com.staging.sg.visa.common.online.*;
import org.jpos.iso.ISOMsg;
import org.junit.jupiter.api.Test;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class VisaNetSimulatorServiceTest {
    @Test
    void returnsStableReferencesForAnExactReplay() throws Exception {
        ISOMsg request = new ISOMsg("0100");
        request.set(2, "4111111111111111"); request.set(3, "000000");
        request.set(4, "000000001000"); request.set(7, "0802123456");
        request.set(11, "000001"); request.set(37, "621512000001");
        VisaOnlineMessageCodec codec = new VisaOnlineMessageCodec();
        VisaOnlineNetworkEnvelope envelope = new VisaOnlineNetworkEnvelope("1.0", "TX-1", "C-1", "I-1",
                Base64.getEncoder().encodeToString(codec.pack(request)), "SIMULATED_NETWORK");
        VisaNetSimulatorService service = new VisaNetSimulatorService("00", "Y");

        VisaOnlineNetworkEnvelope first = service.exchange(envelope);
        VisaOnlineNetworkEnvelope replay = service.exchange(envelope);
        ISOMsg response = codec.unpack(Base64.getDecoder().decode(first.isoMessageBase64()));

        assertThat(replay).isSameAs(first);
        assertThat(response.getMTI()).isEqualTo("0110");
        assertThat(response.getString(39)).isEqualTo("00");
        assertThat(VisaField62Codec.decode(response.getString(62)).transactionId()).hasSize(15);
    }
}
