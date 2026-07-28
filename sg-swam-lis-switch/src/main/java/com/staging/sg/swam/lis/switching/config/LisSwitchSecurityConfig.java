package com.staging.sg.swam.lis.switching.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Politique explicite du service LIS switch en mode LAB/RECETTE local.
 */
@Configuration
public class LisSwitchSecurityConfig {

    @Bean
    SecurityFilterChain lisSwitchSecurityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/clearing/**").permitAll()
                        .anyRequest().denyAll())
                .build();
    }
}
