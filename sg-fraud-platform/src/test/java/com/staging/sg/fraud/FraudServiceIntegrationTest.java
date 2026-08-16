package com.staging.sg.fraud;

import com.staging.sg.fraud.api.FraudApi.*;
import com.staging.sg.fraud.service.FraudService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
class FraudServiceIntegrationTest {
    @Autowired FraudService service;
    @Test void enrollmentAndScoringAreIdempotentAndAlertOnly(){
        EnrollmentRequest enrollment=new EnrollmentRequest("tok_test_abc","MAD","MAR","customer-42");
        var first=service.enroll("BANK_A",enrollment); var replay=service.enroll("BANK_A",enrollment);
        assertThat(replay.enrollmentId()).isEqualTo(first.enrollmentId());
        ScoreRequest request=new ScoreRequest("tx-001","tok_test_abc",1500000,"MAD","MAR","7995","ECOMMERCE",false,false,8,"device-token");
        var score=service.score("BANK_A",request); var scoreReplay=service.score("BANK_A",request);
        assertThat(scoreReplay.assessmentId()).isEqualTo(score.assessmentId());
        assertThat(score.score()).isGreaterThanOrEqualTo(650);
        assertThat(score.enforcedAction()).isEqualTo("ALERT");
        assertThat(score.alertId()).isNotNull(); assertThat(score.reasons()).isNotEmpty();
    }
    @Test void memberIsolationAndRawPanRejectionAreEnforced(){
        service.enroll("BANK_A",new EnrollmentRequest("tok_private_a","MAD","MAR",null));
        var request=new ScoreRequest("tx-a","tok_private_a",100,"MAD","MAR","5411","POS",true,true,0,null);
        assertThatThrownBy(()->service.score("BANK_B",request)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Enrollment");
        assertThatThrownBy(()->service.enroll("BANK_A",new EnrollmentRequest("4111111111111111","MAD","MAR",null))).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("forbidden");
        assertThat(service.listAlerts("BANK_B")).isEmpty();
    }
    @Test void feedbackAndThreatSignalStayMemberScoped(){
        service.enroll("BANK_A",new EnrollmentRequest("tok_feedback","MAD","MAR",null));
        var scored=service.score("BANK_A",new ScoreRequest("tx-feedback","tok_feedback",2000000,"MAD","MAR","7995","ECOMMERCE",false,false,9,null));
        var result=service.feedback("BANK_A",scored.alertId(),new FeedbackRequest("CONFIRMED_FRAUD","lab evidence"));
        assertThat(result.outcome()).isEqualTo("CONFIRMED_FRAUD");
        assertThatThrownBy(()->service.feedback("BANK_B",scored.alertId(),new FeedbackRequest("LEGITIMATE",null))).isInstanceOf(NoSuchElementException.class);
        assertThat(service.addThreatSignal("BANK_A",new ThreatSignalRequest("DEVICE","a".repeat(64),80,"LAB",null)).status()).isEqualTo("ACTIVE");
        var fraudCase=service.openCase("BANK_A",new CaseRequest(scored.alertId(),"Investigation laboratoire"));
        assertThat(service.openCase("BANK_A",new CaseRequest(scored.alertId(),"Rejeu")).id()).isEqualTo(fraudCase.id());
        assertThat(service.listCases("BANK_B")).isEmpty();
        var candidate=service.backtest("BANK_A",new ControlBacktestRequest("Velocity candidate",1000,90,5,10));
        assertThat(candidate.governanceDecision()).isEqualTo("ELIGIBLE_FOR_REVIEW");
    }
    @Test void labBatchUsesSameAlertOnlyScoringPath(){
        service.enroll("LAB_BANK",new EnrollmentRequest("tok_lab_1","MAD","MAR",null));
        service.enroll("LAB_BANK",new EnrollmentRequest("tok_lab_2","MAD","MAR",null));
        var safe=new ScoreRequest("lab-safe","tok_lab_1",1000,"MAD","MAR","5411","POS",true,true,0,null);
        var suspect=new ScoreRequest("lab-risk","tok_lab_2",2000000,"MAD","MAR","7995","ECOMMERCE",false,false,10,null);
        var batch=service.batchScore("LAB_BANK",new BatchScoreRequest(List.of(safe,suspect)));
        assertThat(batch.assessed()).isEqualTo(2); assertThat(batch.alerts()).isEqualTo(1);
        assertThat(batch.results()).extracting(ScoreResponse::enforcedAction).containsExactly("ALLOW","ALERT");
    }
    @Test void collectiveGraphDetectsOneHundredCoordinatedInstrumentsWithoutCrossMemberLeak(){
        ScoreResponse last=null;String sharedDevice="shared-device-group-100";
        for(int i=1;i<=100;i++){
            String token="tok_group_"+i;service.enroll("BANK_GRAPH",new EnrollmentRequest(token,"MAD","MAR","customer-"+i));
            last=service.score("BANK_GRAPH",new ScoreRequest("group-tx-"+i,token,1000,"MAD","MAR","5411","MOBILE",false,true,0,sharedDevice,"customer-"+i,"account-"+i,"beneficiary-shared",null,"ip-shared","MOBILE_BANKING"));
        }
        assertThat(last).isNotNull();assertThat(last.collectiveGroupSize()).isEqualTo(100);
        assertThat(last.collectiveRiskScore()).isGreaterThanOrEqualTo(650);
        assertThat(last.reasons()).extracting(RiskReason::code).contains("COLLECTIVE_PATTERN");
        service.enroll("BANK_OTHER",new EnrollmentRequest("tok_other_graph","MAD","MAR",null));
        var isolated=service.score("BANK_OTHER",new ScoreRequest("other-graph-tx","tok_other_graph",1000,"MAD","MAR","5411","MOBILE",false,true,0,sharedDevice));
        assertThat(isolated.collectiveGroupSize()).isEqualTo(1);
    }
    @Test void activeDecisionPolicyIsExplicitAndMemberScoped(){
        var initial=service.getPolicy("BANK_DECISION");assertThat(initial.mode()).isEqualTo("ALERT_ONLY");
        service.enroll("BANK_DECISION",new EnrollmentRequest("tok_decision","MAD","MAR",null));
        var alertOnly=service.score("BANK_DECISION",new ScoreRequest("decision-1","tok_decision",2000000,"MAD","MAR","7995","ECOMMERCE",false,false,10,null));
        assertThat(alertOnly.recommendedAction()).isEqualTo("BLOCK");assertThat(alertOnly.enforcedAction()).isEqualTo("ALERT");
        service.updatePolicy("BANK_DECISION",new DecisionPolicyRequest("ACTIVE_DECISION",true,true,true));
        var active=service.score("BANK_DECISION",new ScoreRequest("decision-2","tok_decision",2000000,"MAD","MAR","7995","ECOMMERCE",false,false,10,null));
        assertThat(active.enforcedAction()).isEqualTo("BLOCK");
        assertThat(service.getPolicy("BANK_OTHER_DECISION").mode()).isEqualTo("ALERT_ONLY");
    }
}
