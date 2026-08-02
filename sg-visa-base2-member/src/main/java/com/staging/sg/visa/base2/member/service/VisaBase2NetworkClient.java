package com.staging.sg.visa.base2.member.service;

import com.staging.sg.visa.base2.common.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class VisaBase2NetworkClient implements VisaBase2NetworkPort {
    private final boolean enabled; private final RestClient client;
    public VisaBase2NetworkClient(@Value("${visa.base2.network.enabled:false}") boolean enabled,
            @Value("${visa.base2.network.base-url:http://127.0.0.1:8567}") String baseUrl) {
        this.enabled = enabled; this.client = RestClient.builder().baseUrl(baseUrl).build();
    }
    @Override public VisaBase2NetworkAck send(VisaBase2NetworkFileEnvelope envelope) {
        if (!enabled) throw new IllegalStateException("Visa Base II network downstream is disabled");
        VisaBase2NetworkAck ack = client.post().uri("/api/visa/base2/network/v1/files")
                .body(envelope).retrieve().body(VisaBase2NetworkAck.class);
        if (ack == null || !"SIMULATED_NETWORK".equals(ack.provenance()))
            throw new IllegalStateException("Invalid Visa Base II simulator acknowledgement");
        return ack;
    }
}
