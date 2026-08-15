package com.staging.sg.fraud.gateway.config;

import com.staging.sg.fraud.gateway.crypto.ExternalHsmRequired;
import com.staging.sg.fraud.gateway.crypto.FraudKeyExchangeHsm;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FraudHsmConfiguration {

    @Bean
    @ConditionalOnMissingBean(FraudKeyExchangeHsm.class)
    FraudKeyExchangeHsm externalHsmRequired() {
        return new ExternalHsmRequired();
    }
}
