package com.staging.sg.member.bff.config;

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
public class SwitchSecurityConfig {
    @Bean
    SecurityFilterChain switchSecurity(HttpSecurity http) throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/switch/v1/health", "/api/switch/v1/product", "/auth/login",
                                "/api/me/navigation", "/api/admin/users/**", "/api/admin/roles/**",
                                "/api/admin/deployments/**", "/api/switch/v1/interfaces/**",
                                "/api/switch/v1/acquiring/**", "/api/switch/v1/domains/**").permitAll()
                        .requestMatchers("/api/switch/v1/fraud/**").permitAll()
                        .anyRequest().denyAll())
                .build();
    }
    @Bean CorsConfigurationSource corsSource(){CorsConfiguration config=new CorsConfiguration();config.setAllowedOrigins(List.of("http://localhost:4220"));config.setAllowedMethods(List.of("GET","POST","PUT","DELETE","OPTIONS"));config.setAllowedHeaders(List.of("Authorization","Content-Type","Accept"));UrlBasedCorsConfigurationSource source=new UrlBasedCorsConfigurationSource();source.registerCorsConfiguration("/**",config);return source;}
}
