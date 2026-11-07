package com.staging.sg.fraud.portal;

import com.staging.sg.fraud.portal.config.FraudPortalProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(FraudPortalProperties.class)
public class FraudPortalBffApplication {
    public static void main(String[] args) {
        SpringApplication.run(FraudPortalBffApplication.class, args);
    }
}
