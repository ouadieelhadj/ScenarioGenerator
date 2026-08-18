package com.staging.sg.softpos.config;

import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SoftPosSecurityConfig {
    @Bean @Profile("!softpos-lab")
    SecurityFilterChain productionSecurity(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers("/api/admin/softpos/**").hasAuthority("SCOPE_softpos.admin")
                        .requestMatchers("/api/softpos/**").hasAuthority("SCOPE_softpos.transact")
                        .anyRequest().denyAll())
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> {}));
        return http.build();
    }

    @Bean @Profile("softpos-lab")
    SecurityFilterChain laboratorySecurity(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable()).authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
}
