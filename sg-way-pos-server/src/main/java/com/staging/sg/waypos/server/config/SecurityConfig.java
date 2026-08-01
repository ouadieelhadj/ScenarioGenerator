package com.staging.sg.waypos.server.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@Profile("connected-e2e")
public class SecurityConfig {
    @Bean
    SecurityFilterChain wayPosSecurityFilterChain(HttpSecurity http)
            throws Exception {
        http.csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(requests ->
                        requests.anyRequest().permitAll());
        return http.build();
    }
}
