package com.staging.sg.fraud;

import com.staging.sg.fraud.api.FraudApi.EnrollmentRequest;
import com.staging.sg.fraud.service.FraudService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc
class CommercialRiskApiIntegrationTest {
    @Autowired MockMvc mvc; @Autowired FraudService fraud;

    @Test void commercialContractPublishesScoreOnOneHundredAndAccountTakeoverExplanation() throws Exception {
        fraud.enroll("BANK_COMMERCIAL",new EnrollmentRequest("tok-commercial","MAD","MAR","customer-commercial"));
        String body="""
                {"transaction_id":"TX982734","customer_id":"CUS_TOKEN","account_id":"ACC_TOKEN",
                 "card_token":"tok-commercial","channel":"CARD","type":"PURCHASE","amount":1850000,
                 "currency":"MAD","merchant_id":"M12345","device_id":"DEVICE_HASH","ip":"IP_HASH",
                 "country":"MA","mcc":"7995","card_present":false,"strong_authentication":false,
                 "attempts_last_hour":8,"signals":{"NEW_DEVICE":true,"NEW_LOCATION":true,"SESSION_RISK":true}}
                """;
        mvc.perform(post("/v1/risk/score")
                .with(jwt().jwt(j->j.audience(java.util.List.of("futurpayment-fraud")).claim("member_id","BANK_COMMERCIAL"))
                        .authorities(new SimpleGrantedAuthority("SCOPE_fraud.write")))
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transaction_id").value("TX982734"))
                .andExpect(jsonPath("$.risk_score").value(100))
                .andExpect(jsonPath("$.decision").value("BLOCK"))
                .andExpect(jsonPath("$.enforced_action").value("ALERT"))
                .andExpect(jsonPath("$.fraud_type").value("ACCOUNT_TAKEOVER"))
                .andExpect(jsonPath("$.reasons").isArray())
                .andExpect(jsonPath("$.model_version").exists());
    }

    @Test void commercialContractRequiresAuthenticationAndProtectedInstrument() throws Exception {
        String body="{\"transaction_id\":\"TX-NO-AUTH\",\"channel\":\"CARD\",\"type\":\"PURCHASE\",\"amount\":1,\"currency\":\"MAD\",\"country\":\"MA\"}";
        mvc.perform(post("/v1/risk/score").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isUnauthorized());
    }
}
