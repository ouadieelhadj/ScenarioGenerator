package com.staging.sg.way4aura.api;

import com.staging.sg.way4aura.config.SecurityConfig;
import com.staging.sg.way4aura.service.Way4DryRunService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(Way4DryRunController.class)
@Import(SecurityConfig.class)
class Way4ConnectorSecurityTest {
    @Autowired MockMvc mvc;
    @MockBean Way4DryRunService service;
    @MockBean JwtDecoder decoder;

    @Test
    void rejectsMissingToken() throws Exception {
        mvc.perform(request()).andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsInvalidToken() throws Exception {
        when(decoder.decode("invalid")).thenThrow(new JwtValidationException("invalid",
                List.of(new OAuth2Error("invalid_token"))));
        mvc.perform(request().header("Authorization", "Bearer invalid"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsWrongAudience() throws Exception {
        mvc.perform(request().with(jwt().jwt(token -> token.audience(List.of("another-service")))
                        .authorities(() -> "SCOPE_way4.generate")))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsMissingScope() throws Exception {
        mvc.perform(request().with(jwt().jwt(token -> token.audience(List.of("way4-aura-connector")))
                        .authorities(() -> "SCOPE_other")))
                .andExpect(status().isForbidden());
    }

    @Test
    void acceptsExpectedAudienceAndScope() throws Exception {
        when(service.generate(any())).thenReturn(new Way4DryRunService.DryRunResult(
                UUID.randomUUID(), 1, "WAY4-TEST.xml", "VALIDATED",
                "payload", "xml", "xsd", 1, "<xml/>"));
        mvc.perform(request().with(jwt().jwt(token -> token.audience(List.of("way4-aura-connector")))
                        .authorities(() -> "SCOPE_way4.generate")))
                .andExpect(status().isOk());
        verify(service).generate(any());
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request() {
        return post("/api/internal/way4-aura/v1/dry-runs")
                .contentType("application/json")
                .content("{}");
    }
}
