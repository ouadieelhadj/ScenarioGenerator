package com.staging.sg.waypos.simulator.config;

import com.staging.sg.common.iso.WayPosPackager;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(SimulatorProperties.class)
public class SimulatorConfiguration {
    @Bean
    WayPosPackager wayPosPackager() {
        return new WayPosPackager();
    }
}
