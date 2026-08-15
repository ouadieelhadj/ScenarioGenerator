package com.staging.sg.switchlab.bff.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import java.util.List;

@Configuration
public class SwitchLabSecurityConfig {
    @Bean
    SecurityFilterChain switchLabSecurity(HttpSecurity http) throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/auth/login",
                                "/api/me/navigation",
                                "/api/admin/users/**",
                                "/api/admin/roles/**",
                                "/api/admin/deployments/**",
                                "/api/campaigns/**",
                                "/api/executions/**",
                                "/api/admin/tests/**",
                                "/api/tests/my",
                                "/api/networks/**",
                                "/api/admin/message-types/**",
                                "/api/switchlab/v1/health",
                                "/api/switchlab/v1/product",
                                "/api/switchlab/v1/environments",
                                "/api/switchlab/v1/overview",
                                "/api/switchlab/v1/traces").permitAll()
                        .requestMatchers("/api/switchlab/v1/pos/**").permitAll()
                        .requestMatchers("/api/switchlab/v1/test-center/**").permitAll()
                        .requestMatchers("/api/switchlab/v1/fraud/**").permitAll()
                        .anyRequest().denyAll())
                .build();
    }

    @Bean
    CorsConfigurationSource corsSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of("http://localhost:4210"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
