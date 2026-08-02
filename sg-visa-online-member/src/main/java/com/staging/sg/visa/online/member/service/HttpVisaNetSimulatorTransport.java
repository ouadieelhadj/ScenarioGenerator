package com.staging.sg.visa.online.member.service;

import com.staging.sg.visa.common.online.VisaOnlineNetworkEnvelope;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class HttpVisaNetSimulatorTransport implements VisaNetTransport {
    private final boolean enabled;
    private final RestClient client;

    public HttpVisaNetSimulatorTransport(
            @Value("${visa.online.network.enabled:false}") boolean enabled,
            @Value("${visa.online.network.base-url:http://127.0.0.1:8565}") String baseUrl) {
        this.enabled = enabled;
        this.client = RestClient.builder().baseUrl(baseUrl).build();
    }

    @Override public VisaOnlineNetworkEnvelope exchange(VisaOnlineNetworkEnvelope envelope) {
        if (!enabled) throw new IllegalStateException("VisaNet downstream is disabled");
        VisaOnlineNetworkEnvelope response = client.post().uri("/api/visa/network/v1/messages")
                .body(envelope).retrieve().body(VisaOnlineNetworkEnvelope.class);
        if (response == null || !"SIMULATED_NETWORK".equals(response.provenance()))
            throw new IllegalStateException("Invalid VisaNet simulator response");
        return response;
    }
}
