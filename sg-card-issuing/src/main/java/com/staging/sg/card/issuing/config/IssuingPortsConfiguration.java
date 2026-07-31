package com.staging.sg.card.issuing.config;

import com.staging.sg.card.issuing.port.PanVaultPort;
import com.staging.sg.card.issuing.port.PanVaultUnavailableException;
import com.staging.sg.card.issuing.port.CardSecurityPort;
import com.staging.sg.card.issuing.port.FundingAuthorizationPort;
import com.staging.sg.card.issuing.port.PaymentIdentifierResolutionPort;
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

    @Bean
    @ConditionalOnMissingBean(PaymentIdentifierResolutionPort.class)
    PaymentIdentifierResolutionPort unavailableIdentifierResolver() {
        return (issuerId, type, identifier) -> {
            throw new PanVaultUnavailableException();
        };
    }

    @Bean
    @ConditionalOnMissingBean(FundingAuthorizationPort.class)
    FundingAuthorizationPort unavailableFundingPort() {
        return command -> new FundingAuthorizationPort.FundingResult(
                FundingAuthorizationPort.FundingStatus.UNAVAILABLE,
                "CORE_BANKING_UNAVAILABLE", 0, null);
    }

    @Bean
    @ConditionalOnMissingBean(CardSecurityPort.class)
    CardSecurityPort unavailableSecurityPort() {
        return command -> new CardSecurityPort.SecurityResult(
                CardSecurityPort.SecurityStatus.UNAVAILABLE,
                "HSM_UNAVAILABLE", null);
    }
}
