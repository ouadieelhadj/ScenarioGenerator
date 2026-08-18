package com.staging.sg.fraud;

import com.staging.sg.fraud.api.FraudApi.*;
import com.staging.sg.fraud.api.FraudGovernanceApi.*;
import com.staging.sg.fraud.service.FraudGovernanceService;
import com.staging.sg.fraud.service.FraudService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
class FraudGovernanceIntegrationTest {
    @Autowired FraudGovernanceService governance;
    @Autowired FraudService fraud;

    @Test void graphPolicyCanEnableOrPreventCrossSectorCollectiveDetection(){
        governance.updateGraph("BANK_OMNI","MONETIQUE",new GraphPolicyRequest(true,true,"DEVICE,ACCOUNT,IP",2,500,20,800,1440,1,2));
        governance.updateGraph("BANK_OMNI","MOBILE_BANKING",new GraphPolicyRequest(true,false,"DEVICE,ACCOUNT,IP",2,500,20,800,1440,1,2));
        fraud.enroll("BANK_OMNI",new EnrollmentRequest("tok-omni-card","MAD","MAR",null));
        fraud.enroll("BANK_OMNI",new EnrollmentRequest("tok-omni-wallet","MAD","MAR",null));
        fraud.score("BANK_OMNI",new ScoreRequest("omni-card","tok-omni-card",100,"MAD","MAR","5411","POS",true,true,0,"shared-omni-device",null,null,null,null,null,"MONETIQUE"));
        var mobile=fraud.score("BANK_OMNI",new ScoreRequest("omni-mobile","tok-omni-wallet",100,"MAD","MAR","5411","MOBILE_BANKING",true,true,0,"shared-omni-device",null,"mobile-account",null,null,null,"MOBILE_BANKING"));
        assertThat(mobile.collectiveGroupSize()).isEqualTo(1);

        governance.updateGraph("BANK_OMNI","MOBILE_BANKING",new GraphPolicyRequest(true,true,"DEVICE,ACCOUNT,IP",2,500,20,800,1440,1,2));
        fraud.enroll("BANK_OMNI",new EnrollmentRequest("tok-omni-wallet-2","MAD","MAR",null));
        var crossSector=fraud.score("BANK_OMNI",new ScoreRequest("omni-mobile-cross","tok-omni-wallet-2",100,"MAD","MAR","5411","MOBILE_BANKING",true,true,0,"shared-omni-device",null,"mobile-account-2",null,null,null,"MOBILE_BANKING"));
        assertThat(crossSector.collectiveGroupSize()).isEqualTo(3);
        assertThat(crossSector.reasons()).extracting(RiskReason::code).contains("COLLECTIVE_PATTERN");
    }

    @Test void aiPolicyIsMemberSectorScopedAndRequiresHumanApproval(){
        AiPolicyRequest active=new AiPolicyRequest(true,"ACTIVE","champion-v3","challenger-v4",10,.85,.75,.03,.15,"HEALTHY",true,true,300,600,780,900);
        var saved=governance.updateAi("BANK_AI","MOBILE_BANKING",active);
        assertThat(saved.championModel()).isEqualTo("champion-v3");
        assertThat(governance.ai("BANK_AI","MOBILE_BANKING").challengerTrafficPercent()).isEqualTo(10);
        assertThat(governance.ai("BANK_OTHER","MOBILE_BANKING").enabled()).isFalse();
        AiPolicyRequest unsafe=new AiPolicyRequest(true,"ACTIVE","champion-v3",null,0,.8,.7,.05,.2,"HEALTHY",true,false,350,650,800,900);
        assertThatThrownBy(()->governance.updateAi("BANK_AI","MONETIQUE",unsafe)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("mandatory");
    }
    @Test void mobileAccountCanBeEnrolledAndScoredWithoutCardEnrollment(){
        var enrolled=fraud.enrollSubject("BANK_MOBILE",new MonitoringSubjectEnrollmentRequest("ACCOUNT","account-mobile-001","MOBILE_BANKING","MOBILE_APP"));
        assertThat(enrolled.status()).isEqualTo("MONITORED");
        ScoreRequest request=new ScoreRequest("mobile-subject-tx",null,500,"MAD","MAR","0000","MOBILE_APP",false,true,1,"mobile-device",null,"account-mobile-001",null,null,"mobile-ip","MOBILE_BANKING",java.util.Map.of(),"ACCOUNT","account-mobile-001");
        var scored=fraud.score("BANK_MOBILE",request);
        assertThat(scored.assessmentId()).isNotNull();
        assertThat(fraud.score("BANK_MOBILE",request).assessmentId()).isEqualTo(scored.assessmentId());
    }
}
