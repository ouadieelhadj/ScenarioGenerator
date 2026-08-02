package com.staging.sg.visa.visanet.simulator.config;

import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration @Profile("connected-e2e")
public class ConnectedE2eSecurityConfig {
    @Bean SecurityFilterChain testSecurity(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable()).authorizeHttpRequests(requests -> requests.anyRequest().permitAll());
        return http.build();
    }
}
