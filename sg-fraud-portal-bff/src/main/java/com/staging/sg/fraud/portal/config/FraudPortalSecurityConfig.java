package com.staging.sg.fraud.portal.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProvider;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestCustomizers;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

@Configuration
public class FraudPortalSecurityConfig {
    @Bean
    OAuth2AuthorizedClientManager fraudPortalAuthorizedClientManager(
            ClientRegistrationRepository registrations,
            OAuth2AuthorizedClientService authorizedClients) {
        OAuth2AuthorizedClientProvider provider = OAuth2AuthorizedClientProviderBuilder.builder()
                .authorizationCode()
                .refreshToken()
                .build();
        AuthorizedClientServiceOAuth2AuthorizedClientManager manager =
                new AuthorizedClientServiceOAuth2AuthorizedClientManager(registrations, authorizedClients);
        manager.setAuthorizedClientProvider(provider);
        return manager;
    }

    @Bean
    SecurityFilterChain fraudPortalSecurity(HttpSecurity http,
                                            ClientRegistrationRepository clients,
                                            FraudPortalProperties properties) throws Exception {
        CookieCsrfTokenRepository csrf = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrf.setCookiePath("/");
        LogoutSuccessHandler oidcLogout = oidcLogoutSuccessHandler(properties);
        return http
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .csrf(configurer -> configurer.csrfTokenRepository(csrf)
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers("/", "/index.html", "/assets/**", "/*.js", "/*.css", "/*.ico",
                                "/api/fraud-portal/v1/health", "/api/fraud-portal/v1/config", "/api/fraud-portal/v1/csrf",
                                "/oauth2/**", "/login/**", "/error").permitAll()
                        .requestMatchers("/api/fraud-portal/v1/**").authenticated()
                        .anyRequest().denyAll())
                .exceptionHandling(exceptions -> exceptions.defaultAuthenticationEntryPointFor(
                        new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                        new AntPathRequestMatcher("/api/**")))
                .oauth2Login(oauth2 -> oauth2
                        .authorizationEndpoint(endpoint -> endpoint
                                .authorizationRequestResolver(pkceAuthorizationRequestResolver(clients)))
                        .defaultSuccessUrl(properties.mode() == FraudPortalProperties.Mode.GATEWAY
                                ? "/gateway-test/" : "/", true))
                .oauth2Client(Customizer.withDefaults())
                .logout(logout -> logout.logoutUrl("/api/fraud-portal/v1/logout")
                        .logoutSuccessHandler(oidcLogout)
                        .clearAuthentication(true).invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID", "XSRF-TOKEN"))
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                "default-src 'self'; connect-src 'self'; img-src 'self' data:; "
                                        + "style-src 'self' 'unsafe-inline'; script-src 'self'; frame-ancestors 'none'")))
                .build();
    }

    private LogoutSuccessHandler oidcLogoutSuccessHandler(FraudPortalProperties properties) {
        return (request, response, authentication) -> {
            String postLogoutRedirect = ServletUriComponentsBuilder.fromContextPath(request)
                    .path("/login").queryParam("logout").build().toUriString();
            if (authentication == null || !(authentication.getPrincipal() instanceof OidcUser oidcUser)
                    || oidcUser.getIdToken() == null) {
                response.sendRedirect(postLogoutRedirect);
                return;
            }
            String target = UriComponentsBuilder.fromUri(properties.oidcEndSessionUri())
                    .queryParam("id_token_hint", oidcUser.getIdToken().getTokenValue())
                    .queryParam("post_logout_redirect_uri", postLogoutRedirect)
                    .build().encode().toUriString();
            response.sendRedirect(target);
        };
    }

    private OAuth2AuthorizationRequestResolver pkceAuthorizationRequestResolver(
            ClientRegistrationRepository clients) {
        DefaultOAuth2AuthorizationRequestResolver resolver =
                new DefaultOAuth2AuthorizationRequestResolver(clients, "/oauth2/authorization");
        resolver.setAuthorizationRequestCustomizer(OAuth2AuthorizationRequestCustomizers.withPkce());
        return resolver;
    }

    @RestController
    static class CsrfController {
        @GetMapping("/api/fraud-portal/v1/csrf")
        Map<String, String> csrf(CsrfToken token) {
            return Map.of("headerName", token.getHeaderName(), "parameterName", token.getParameterName(),
                    "token", token.getToken());
        }
    }
}
