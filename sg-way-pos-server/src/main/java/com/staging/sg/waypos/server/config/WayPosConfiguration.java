package com.staging.sg.waypos.server.config;

import com.staging.sg.common.iso.WayPosPackager;
import com.staging.sg.common.iso.crypto.JposHsmService;
import com.staging.sg.common.emv.McDmasEmv;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(WayPosProperties.class)
public class WayPosConfiguration {
    @Bean
    WayPosPackager wayPosPackager() {
        return new WayPosPackager();
    }

    @Bean
    RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }

    @Bean
    JposHsmService wayPosHsmService() {
        return new JposHsmService();
    }

    @Bean
    McDmasEmv wayPosEmv(JposHsmService hsm) {
        return new McDmasEmv(hsm);
    }
}
