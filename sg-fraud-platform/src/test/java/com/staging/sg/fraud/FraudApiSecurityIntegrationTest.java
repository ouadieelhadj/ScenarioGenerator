package com.staging.sg.fraud;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc
class FraudApiSecurityIntegrationTest {
    @Autowired MockMvc mvc;
    @Test void publicHealthButProtectedOperations() throws Exception {
        mvc.perform(get("/api/fraud/v1/health")).andExpect(status().isOk()).andExpect(jsonPath("$.mode").value("ALERT_ONLY"));
        mvc.perform(get("/api/fraud/v1/alerts")).andExpect(status().isUnauthorized());
    }
    @Test void memberClaimAndAudienceAuthorizeTokenizedEnrollment() throws Exception {
        String body="{\"tokenReference\":\"tok_api_bank_a\",\"currency\":\"MAD\",\"country\":\"MAR\"}";
        mvc.perform(post("/api/fraud/v1/cards/monitoring-enrollments")
                .with(jwt().jwt(j->j.audience(java.util.List.of("futurpayment-fraud")).claim("member_id","BANK_API_A")).authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("SCOPE_fraud.write")))
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("MONITORED"));
    }
    @Test void missingMemberClaimAndRawPanFailClosed() throws Exception {
        String body="{\"tokenReference\":\"4111111111111111\",\"currency\":\"MAD\",\"country\":\"MAR\"}";
        mvc.perform(post("/api/fraud/v1/cards/monitoring-enrollments")
                .with(jwt().jwt(j->j.audience(java.util.List.of("futurpayment-fraud")).claim("member_id","BANK_API_B")).authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("SCOPE_fraud.write")))
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        mvc.perform(post("/api/fraud/v1/cards/monitoring-enrollments")
                .with(jwt().jwt(j->j.audience(java.util.List.of("futurpayment-fraud"))).authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("SCOPE_fraud.write")))
                .contentType(MediaType.APPLICATION_JSON).content("{\"tokenReference\":\"tok_missing_member\",\"currency\":\"MAD\",\"country\":\"MAR\"}"))
                .andExpect(status().isForbidden());
    }
}
