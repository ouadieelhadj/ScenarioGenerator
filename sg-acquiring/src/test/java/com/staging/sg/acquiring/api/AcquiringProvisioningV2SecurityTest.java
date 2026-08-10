package com.staging.sg.acquiring.api;

import com.staging.sg.acquiring.config.SecurityConfig;
import com.staging.sg.acquiring.service.MerchantProvisioningV2Service;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AcquiringProvisioningV2Controller.class)
@Import(SecurityConfig.class)
class AcquiringProvisioningV2SecurityTest {
    @Autowired MockMvc mvc;
    @MockBean MerchantProvisioningV2Service service;
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
                        .authorities(() -> "SCOPE_merchant.provision")))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsMissingScope() throws Exception {
        mvc.perform(request().with(jwt().jwt(token -> token.audience(List.of("futurpayment-acquiring")))
                        .authorities(() -> "SCOPE_other")))
                .andExpect(status().isForbidden());
    }

    @Test
    void acceptsValidAudienceAndScopeAndPreservesHeaders() throws Exception {
        when(service.provision(any(), eq("idem-1"), eq("corr-1"))).thenReturn(
                new MerchantProvisioningResultV2("2.0", UUID.randomUUID(),
                        "123456789012345", "COMPLETED", List.of()));
        mvc.perform(request().with(jwt().jwt(token -> token.audience(List.of("futurpayment-acquiring")))
                        .authorities(() -> "SCOPE_merchant.provision")))
                .andExpect(status().isOk());
        verify(service).provision(any(), eq("idem-1"), eq("corr-1"));
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request() {
        return post("/api/internal/acquiring/v2/merchant-provisioning")
                .contentType("application/json")
                .header("Idempotency-Key", "idem-1")
                .header("X-Correlation-ID", "corr-1")
                .content("""
                    {"schemaVersion":"2.0","onboardingCaseId":"aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
                     "onboardingReference":"ONB-TEST","acquirerId":"ACQ","outlets":[]}
                    """);
    }
}
