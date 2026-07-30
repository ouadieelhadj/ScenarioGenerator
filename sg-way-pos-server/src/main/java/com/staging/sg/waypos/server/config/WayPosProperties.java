package com.staging.sg.waypos.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

@ConfigurationProperties(prefix = "way-pos")
public record WayPosProperties(
        int isoPort,
        int t1Seconds,
        String panPepper,
        String outboxKeyHex,
        Map<String, String> connectors) {
}
