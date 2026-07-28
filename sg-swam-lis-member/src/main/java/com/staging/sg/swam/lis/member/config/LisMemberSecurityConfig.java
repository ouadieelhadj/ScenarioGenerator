package com.staging.sg.swam.lis.member.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Politique explicite du service LIS membre en mode LAB/RECETTE local.
 * L'API clearing est interne ; son authentification sera portée par le portail
 * ou la passerelle lorsque le RBAC opérationnel sera raccordé.
 */
@Configuration
public class LisMemberSecurityConfig {

    @Bean
    SecurityFilterChain lisMemberSecurityFilterChain(HttpSecurity http) throws Exception {
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
