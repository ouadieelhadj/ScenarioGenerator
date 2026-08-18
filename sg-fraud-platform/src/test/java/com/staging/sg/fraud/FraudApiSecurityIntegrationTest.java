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
    @Test void eventRoutesRequireDedicatedAdminScopeAndStayMemberIsolated() throws Exception {
        String body="{\"topicTemplate\":\"fraud.bank_api_a.monetique.assessment.v1\",\"schemaVersion\":\"v1\",\"retentionClass\":\"STANDARD\",\"enabled\":true,\"priority\":10}";
        var readToken=jwt().jwt(j->j.audience(java.util.List.of("futurpayment-fraud")).claim("member_id","BANK_API_A"))
                .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("SCOPE_fraud.read"));
        mvc.perform(put("/api/fraud/v1/admin/event-routes/MONETIQUE/RISK_ASSESSMENT_COMPLETED")
                .with(readToken).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());

        var adminToken=jwt().jwt(j->j.audience(java.util.List.of("futurpayment-fraud")).claim("member_id","BANK_API_A"))
                .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("SCOPE_fraud.admin"));
        mvc.perform(put("/api/fraud/v1/admin/event-routes/MONETIQUE/RISK_ASSESSMENT_COMPLETED")
                .with(adminToken).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.memberId").value("BANK_API_A"));
        mvc.perform(get("/api/fraud/v1/admin/event-routes").with(adminToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].memberId").value("BANK_API_A"));
    }
    @Test void graphAndAiGovernanceRequireAdminScope() throws Exception {
        var readToken=jwt().jwt(j->j.audience(java.util.List.of("futurpayment-fraud")).claim("member_id","BANK_GOV"))
                .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("SCOPE_fraud.read"));
        mvc.perform(get("/api/fraud/v1/admin/governance/graph/MOBILE_BANKING").with(readToken)).andExpect(status().isForbidden());
        var adminToken=jwt().jwt(j->j.audience(java.util.List.of("futurpayment-fraud")).claim("member_id","BANK_GOV"))
                .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("SCOPE_fraud.admin"));
        mvc.perform(get("/api/fraud/v1/admin/governance/graph/MOBILE_BANKING").with(adminToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.memberId").value("BANK_GOV")).andExpect(jsonPath("$.crossSectorEnabled").value(true));
        mvc.perform(get("/api/fraud/v1/admin/governance/ai/MOBILE_BANKING").with(adminToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.governanceMode").value("SHADOW")).andExpect(jsonPath("$.analystApprovalRequired").value(true));
    }
}
