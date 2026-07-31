package com.staging.sg.card.issuing.config;

import com.staging.sg.card.issuing.port.PanVaultPort;
import com.staging.sg.card.issuing.port.PanVaultUnavailableException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class IssuingPortsConfiguration {
    @Bean
    @ConditionalOnMissingBean(PanVaultPort.class)
    PanVaultPort unavailablePanVault() {
        return command -> {
            throw new PanVaultUnavailableException();
        };
    }
}
