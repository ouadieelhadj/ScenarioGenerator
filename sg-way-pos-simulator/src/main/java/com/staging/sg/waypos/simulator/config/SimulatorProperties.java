package com.staging.sg.waypos.simulator.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

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
        String ansiX917CipherMode,
        String tamkId,
        String tamkHex,
        String tpmkId,
        String tpmkHex) {

    @ConstructorBinding
    public SimulatorProperties {
    }

    public SimulatorProperties(
            String host, int port, int timeoutSeconds, String terminalId,
            String merchantId, String currency, String macMode, String takHex,
            String masterKeyId, String masterKeyType, String masterKeyHex,
            String ansiX917BlockEncoding, String ansiX917CipherMode) {
        this(host, port, timeoutSeconds, terminalId, merchantId, currency,
                macMode, takHex, masterKeyId, masterKeyType, masterKeyHex,
                ansiX917BlockEncoding, ansiX917CipherMode,
                null, null, null, null);
    }
}
