package com.staging.sg.waypos.simulator.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "way-pos-simulator")
public record SimulatorProperties(
        String host,
        int port,
        int timeoutSeconds,
        String terminalId,
        String merchantId,
        String currency,
        String macMode,
        String takHex,
        String masterKeyId,
        String masterKeyType,
        String masterKeyHex,
        String ansiX917BlockEncoding,
        String ansiX917CipherMode) {
}
