package com.staging.sg.fraud.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.staging.sg.fraud.api.FraudApi.*;
import com.staging.sg.fraud.domain.*;
import com.staging.sg.fraud.repository.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
public class FraudService {
    private final CardProfileRepository cards; private final RiskAssessmentRepository assessments;
    private final FraudAlertRepository alerts; private final FraudFeedbackRepository feedback;
    private final ThreatSignalRepository threatSignals; private final ReferenceProtector protector;
    private final FraudCaseRepository cases; private final ControlCandidateRepository controls;
    private final FraudScoringEngine engine; private final ObjectMapper json; private final int alertThreshold;
    public FraudService(CardProfileRepository cards,RiskAssessmentRepository assessments,FraudAlertRepository alerts,
            FraudFeedbackRepository feedback,ThreatSignalRepository threatSignals,FraudCaseRepository cases,
            ControlCandidateRepository controls,ReferenceProtector protector,
            FraudScoringEngine engine,ObjectMapper json,@Value("${fraud.scoring.alert-threshold:650}") int alertThreshold){
        this.cards=cards;this.assessments=assessments;this.alerts=alerts;this.feedback=feedback;this.threatSignals=threatSignals;this.cases=cases;this.controls=controls;
        this.protector=protector;this.engine=engine;this.json=json;this.alertThreshold=alertThreshold;
    }
    @Transactional
    public EnrollmentResponse enroll(String memberId,EnrollmentRequest request){
        String tokenHash=protector.hash(request.tokenReference());
        return cards.findByMemberIdAndTokenHash(memberId,tokenHash).map(this::toEnrollment).orElseGet(()->{
            String customerHash=request.customerReference()==null?null:protector.hash(request.customerReference());
            try{return toEnrollment(cards.saveAndFlush(CardProfile.enroll(memberId,tokenHash,request.currency(),request.country(),customerHash)));}
            catch(DataIntegrityViolationException race){return cards.findByMemberIdAndTokenHash(memberId,tokenHash).map(this::toEnrollment).orElseThrow(()->race);}
        });
    }
    @Transactional
    public ScoreResponse score(String memberId,ScoreRequest request){
        Optional<RiskAssessment> existing=assessments.findByMemberIdAndTransactionReference(memberId,request.transactionReference());
        if(existing.isPresent()) return toScore(memberId,existing.get());
        String tokenHash=protector.hash(request.tokenReference());
        if(cards.findByMemberIdAndTokenHash(memberId,tokenHash).isEmpty()) throw new IllegalArgumentException("Card Monitoring Enrollment required");
        FraudScoringEngine.Result result=engine.score(request);
        try{
            String reasons=json.writeValueAsString(result.reasons());
            RiskAssessment saved=assessments.saveAndFlush(RiskAssessment.create(memberId,request.transactionReference(),tokenHash,result.score(),result.band(),result.recommendedAction(),reasons));
            UUID alertId=null;
            if(saved.score()>=alertThreshold) alertId=alerts.save(FraudAlert.open(memberId,saved)).id();
            return toScore(saved, result.reasons(), alertId);
        }catch(JsonProcessingException e){throw new IllegalStateException("Risk explanation serialization failed",e);}
        catch(DataIntegrityViolationException race){return assessments.findByMemberIdAndTransactionReference(memberId,request.transactionReference()).map(a->toScore(memberId,a)).orElseThrow(()->race);}
    }
    @Transactional(readOnly=true)
    public List<AlertView> listAlerts(String memberId){return alerts.findTop100ByMemberIdOrderByCreatedAtDesc(memberId).stream().map(this::toAlert).toList();}
    @Transactional
    public FeedbackResponse feedback(String memberId,UUID alertId,FeedbackRequest request){
        alerts.findByIdAndMemberId(alertId,memberId).orElseThrow(()->new NoSuchElementException("Alert not found"));
        FraudFeedback saved=feedback.save(FraudFeedback.create(memberId,alertId,request.outcome(),request.comment()));
        return new FeedbackResponse(saved.id(),alertId,saved.outcome(),saved.createdAt());
    }
    @Transactional
    public ThreatSignalResponse addThreatSignal(String memberId,ThreatSignalRequest request){
        if(request.indicatorHash().replaceAll("[ -]","").matches("[0-9]{12,19}"))throw new IllegalArgumentException("Raw card numbers are forbidden");
        ThreatSignal s=threatSignals.save(ThreatSignal.create(memberId,request.indicatorType(),request.indicatorHash(),request.severity(),request.source(),request.expiresAt()));
        return new ThreatSignalResponse(s.id(),"ACTIVE",s.createdAt());
    }
    @Transactional
    public CaseView openCase(String memberId,CaseRequest request){
        alerts.findByIdAndMemberId(request.alertId(),memberId).orElseThrow(()->new NoSuchElementException("Alert not found"));
        FraudCase value=cases.findByMemberIdAndAlertId(memberId,request.alertId()).orElseGet(()->cases.save(FraudCase.open(memberId,request.alertId(),request.title())));
        return new CaseView(value.id(),value.alertId(),value.title(),value.status(),value.createdAt());
    }
    @Transactional(readOnly=true)
    public List<CaseView> listCases(String memberId){return cases.findTop100ByMemberIdOrderByCreatedAtDesc(memberId).stream().map(c->new CaseView(c.id(),c.alertId(),c.title(),c.status(),c.createdAt())).toList();}
    @Transactional
    public ControlBacktestResponse backtest(String memberId,ControlBacktestRequest request){
        if(request.truePositives()+request.falsePositives()>request.labeledTransactions()||request.truePositives()+request.falseNegatives()>request.labeledTransactions())throw new IllegalArgumentException("Inconsistent confusion matrix");
        double precision=ratio(request.truePositives(),request.truePositives()+request.falsePositives());
        double recall=ratio(request.truePositives(),request.truePositives()+request.falseNegatives());
        double fpr=ratio(request.falsePositives(),Math.max(1,request.labeledTransactions()-request.truePositives()-request.falseNegatives()));
        String decision=precision>=0.80&&recall>=0.70&&fpr<=0.05?"ELIGIBLE_FOR_REVIEW":"REJECT_OR_TUNE";
        ControlCandidate value=controls.save(ControlCandidate.backtested(memberId,request.name(),precision,recall,fpr,decision));
        return new ControlBacktestResponse(value.id(),value.status(),value.precision(),value.recall(),value.falsePositiveRate(),value.governanceDecision(),value.createdAt());
    }
    @Transactional
    public BatchScoreResponse batchScore(String memberId,BatchScoreRequest request){
        List<ScoreResponse> results=request.transactions().stream().map(tx->score(memberId,tx)).toList();
        return new BatchScoreResponse(request.transactions().size(),results.size(),(int)results.stream().filter(r->r.alertId()!=null).count(),"LAB_ALERT_ONLY",results);
    }
    private double ratio(int numerator,int denominator){return denominator==0?0d:Math.round((numerator/(double)denominator)*10000d)/10000d;}
    private EnrollmentResponse toEnrollment(CardProfile p){return new EnrollmentResponse(p.id(),p.status(),p.createdAt());}
    private AlertView toAlert(FraudAlert a){return new AlertView(a.id(),a.transactionReference(),a.score(),a.band(),a.status(),a.createdAt());}
    private ScoreResponse toScore(String memberId,RiskAssessment a){
        try{List<RiskReason> reasons=json.readValue(a.reasonsJson(),new TypeReference<>(){});UUID alertId=alerts.findByMemberIdAndTransactionReference(memberId,a.transactionReference()).map(FraudAlert::id).orElse(null);return toScore(a,reasons,alertId);}
        catch(JsonProcessingException e){throw new IllegalStateException("Stored risk explanation is invalid",e);}
    }
    private ScoreResponse toScore(RiskAssessment a,List<RiskReason> reasons,UUID alertId){return new ScoreResponse(a.id(),a.score(),a.band(),a.recommendedAction(),a.enforcedAction(),a.modelVersion(),reasons,alertId,a.createdAt());}
}
