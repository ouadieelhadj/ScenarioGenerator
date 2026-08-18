package com.staging.sg.fraud.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.staging.sg.fraud.api.FraudApi.*;
import com.staging.sg.fraud.domain.*;
import com.staging.sg.fraud.repository.*;
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
    private final FraudEntityLinkRepository entityLinks; private final FraudFeatureSnapshotRepository features;
    private final FraudDecisionPolicyRepository policies; private final FraudCollectiveGraph collectiveGraph;
    private final FraudMonitoringSubjectRepository monitoringSubjects;
    private final FraudEventOutboxRepository outbox; private final FraudScoringEngine engine; private final IndustrialRiskOrchestrator industrial; private final ObjectMapper json;
    public FraudService(CardProfileRepository cards,RiskAssessmentRepository assessments,FraudAlertRepository alerts,
            FraudFeedbackRepository feedback,ThreatSignalRepository threatSignals,FraudCaseRepository cases,
            ControlCandidateRepository controls,ReferenceProtector protector,
            FraudEntityLinkRepository entityLinks,FraudFeatureSnapshotRepository features,FraudDecisionPolicyRepository policies,
            FraudMonitoringSubjectRepository monitoringSubjects,FraudCollectiveGraph collectiveGraph,FraudEventOutboxRepository outbox,FraudScoringEngine engine,IndustrialRiskOrchestrator industrial,ObjectMapper json){
        this.cards=cards;this.assessments=assessments;this.alerts=alerts;this.feedback=feedback;this.threatSignals=threatSignals;this.cases=cases;this.controls=controls;
        this.entityLinks=entityLinks;this.features=features;this.policies=policies;this.monitoringSubjects=monitoringSubjects;this.collectiveGraph=collectiveGraph;
        this.protector=protector;this.outbox=outbox;this.engine=engine;this.industrial=industrial;this.json=json;
    }
    @Transactional
    public EnrollmentResponse enroll(String memberId,EnrollmentRequest request){
        rejectRawCardNumber(request.tokenReference());
        String tokenHash=protector.hash(request.tokenReference());
        EnrollmentResponse response=cards.findByMemberIdAndTokenHash(memberId,tokenHash).map(this::toEnrollment).orElseGet(()->{
            String customerHash=request.customerReference()==null?null:protector.hash(request.customerReference());
            try{return toEnrollment(cards.saveAndFlush(CardProfile.enroll(memberId,tokenHash,request.currency(),request.country(),customerHash)));}
            catch(DataIntegrityViolationException race){return cards.findByMemberIdAndTokenHash(memberId,tokenHash).map(this::toEnrollment).orElseThrow(()->race);}
        });
        ensureSubject(memberId,"MONETIQUE","CARD_TOKEN",tokenHash);
        return response;
    }
    @Transactional
    public MonitoringSubjectEnrollmentResponse enrollSubject(String memberId,MonitoringSubjectEnrollmentRequest request){
        rejectRawCardNumber(request.subjectReference());String sector=FraudSector.normalize(request.sector(),request.channel());String hash=protector.hash(request.subjectReference());
        FraudMonitoringSubject subject=ensureSubject(memberId,sector,request.subjectType(),hash);
        return new MonitoringSubjectEnrollmentResponse(subject.id(),request.subjectType(),sector,subject.status(),subject.createdAt());
    }
    @Transactional
    public ScoreResponse score(String memberId,ScoreRequest request){
        FraudSubjectIdentity subject=FraudSubjectIdentity.resolve(request);String sectorId=subject.sectorId();
        Optional<RiskAssessment> existing=assessments.findByMemberIdAndTransactionReference(memberId,request.transactionReference());
        if(existing.isPresent()) return toScore(memberId,existing.get());
        String subjectHash=protector.hash(subject.subjectReference());
        boolean genericEnrollment=monitoringSubjects.findByMemberIdAndSectorIdAndSubjectTypeAndSubjectHash(memberId,sectorId,subject.subjectType(),subjectHash).isPresent();
        boolean legacyCardEnrollment=request.tokenReference()!=null&&cards.findByMemberIdAndTokenHash(memberId,protector.hash(request.tokenReference())).isPresent();
        if(!genericEnrollment&&!legacyCardEnrollment)throw new IllegalArgumentException("Monitoring Subject Enrollment required");
        FraudCollectiveGraph.Result collective=collectiveGraph.observeAndEvaluate(memberId,subject,subjectHash,request);
        IndustrialRiskOrchestrator.Evaluation industrialResult=industrial.evaluate(memberId,subjectHash,request,collective);
        FraudScoringEngine.Result result=engine.score(request,collective,industrialResult);
        try{
            String reasons=json.writeValueAsString(result.reasons());
            Map<String,Object> featureSnapshot=new LinkedHashMap<>();
            featureSnapshot.put("amountMinor",request.amountMinor());featureSnapshot.put("attemptsLastHour",request.attemptsLastHour());
            featureSnapshot.put("cardPresent",request.cardPresent());featureSnapshot.put("strongAuthentication",request.strongAuthentication());
            featureSnapshot.put("channel",request.channel());featureSnapshot.put("sector",sectorId);featureSnapshot.put("subjectType",subject.subjectType());
            featureSnapshot.put("collectiveGroupSize",result.groupSize());featureSnapshot.put("collectiveRiskScore",result.collectiveScore());
            featureSnapshot.put("observedSignals",request.observedSignals());featureSnapshot.put("industrialFeatures",industrialResult.features());
            featureSnapshot.put("featureSource",industrialResult.featureSource());featureSnapshot.put("modelStatus",industrialResult.modelStatus());
            featureSnapshot.put("aiGovernanceMode",industrialResult.governanceMode());featureSnapshot.put("aiFallbackApplied",industrialResult.fallbackApplied());
            featureSnapshot.put("championShadowScore",industrialResult.championShadowScore());featureSnapshot.put("challengerShadowScore",industrialResult.challengerShadowScore());
            featureSnapshot.put("challengerModelVersion",industrialResult.challengerModelVersion());featureSnapshot.put("modelExplanation",industrialResult.explanation());
            String featureJson=json.writeValueAsString(featureSnapshot);
            features.save(FraudFeatureSnapshot.create(memberId,sectorId,request.transactionReference(),"features-v1",featureJson));
            String enforced=enforcedAction(memberId,result.recommendedAction());
            RiskAssessment saved=assessments.saveAndFlush(RiskAssessment.create(memberId,sectorId,request.transactionReference(),subject.subjectType(),subjectHash,result.score(),result.band(),result.recommendedAction(),enforced,industrialResult.modelVersion(),reasons,result.groupSize(),result.collectiveScore()));
            UUID alertId=null;
            if(!"ALLOW".equals(saved.recommendedAction())) alertId=alerts.save(FraudAlert.open(memberId,saved)).id();
            String event=json.writeValueAsString(Map.of("schemaVersion","1.0","memberId",memberId,"sectorId",sectorId,"assessmentId",saved.id(),"transactionReference",saved.transactionReference(),"score",saved.score(),"recommendedAction",saved.recommendedAction(),"enforcedAction",saved.enforcedAction(),"createdAt",saved.createdAt()));
            outbox.save(FraudEventOutbox.pending(memberId,sectorId,"RISK_ASSESSMENT",saved.transactionReference(),"RiskAssessmentCompleted.v1",event));
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
        return new BatchScoreResponse(request.transactions().size(),results.size(),(int)results.stream().filter(r->r.alertId()!=null).count(),policy(memberId).mode(),results);
    }
    @Transactional
    public DecisionPolicyResponse updatePolicy(String memberId,DecisionPolicyRequest request){
        FraudDecisionPolicy value=policies.findByMemberId(memberId).orElseGet(()->FraudDecisionPolicy.create(memberId));
        if("ALERT_ONLY".equals(request.mode())&&(request.challengeEnabled()||request.holdEnabled()||request.blockEnabled()))
            throw new IllegalArgumentException("Active decisions require ACTIVE_DECISION mode");
        value.update(request.mode(),request.challengeEnabled(),request.holdEnabled(),request.blockEnabled());policies.save(value);return toPolicy(value);
    }
    @Transactional
    public DecisionPolicyResponse getPolicy(String memberId){return toPolicy(policy(memberId));}
    @Transactional(readOnly=true)
    public ScoreResponse assessment(String memberId,UUID assessmentId){
        RiskAssessment value=assessments.findByIdAndMemberId(assessmentId,memberId)
                .orElseThrow(()->new NoSuchElementException("Assessment not found"));
        return toScore(memberId,value);
    }
    @Transactional(readOnly=true)
    public OperationsDashboard dashboard(String memberId){
        List<RiskAssessment> recent=assessments.findTop100ByMemberIdOrderByCreatedAtDesc(memberId);
        Map<String,Long> risks=count(recent.stream().map(RiskAssessment::band).toList());
        Map<String,Long> decisions=count(recent.stream().map(RiskAssessment::enforcedAction).toList());
        List<TopEntity> top=entityLinks.findTop100ByMemberIdOrderByObservationCountDesc(memberId).stream().limit(20).map(this::topEntity).toList();
        return new OperationsDashboard(assessments.countByMemberId(memberId),alerts.countByMemberId(memberId),cases.countByMemberId(memberId),
                risks,decisions,top,recent.stream().limit(20).map(this::summary).toList(),java.time.Instant.now());
    }
    @Transactional(readOnly=true)
    public FraudStory story(String memberId,UUID assessmentId){
        RiskAssessment value=assessments.findByIdAndMemberId(assessmentId,memberId).orElseThrow(()->new NoSuchElementException("Assessment not found"));
        ScoreResponse score=toScore(memberId,value);List<String> codes=score.reasons().stream().map(RiskReason::code).toList();
        List<AssessmentSummary> history=assessments.findTop20ByMemberIdAndSubjectHashOrderByCreatedAtDesc(memberId,value.subjectHash()).stream().map(this::summary).toList();
        List<TopEntity> associated=entityLinks.findTop50ByMemberIdAndSubjectHashOrderByObservationCountDesc(memberId,value.subjectHash()).stream().map(this::topEntity).toList();
        return new FraudStory(value.id(),value.transactionReference(),(int)Math.round(value.score()/10d),value.score(),value.band(),probableType(codes),
                value.recommendedAction(),value.enforcedAction(),score.reasons(),history,associated,value.collectiveGroupSize(),value.collectiveRiskScore(),score.alertId(),java.time.Instant.now());
    }
    private double ratio(int numerator,int denominator){return denominator==0?0d:Math.round((numerator/(double)denominator)*10000d)/10000d;}
    private EnrollmentResponse toEnrollment(CardProfile p){return new EnrollmentResponse(p.id(),p.status(),p.createdAt());}
    private AlertView toAlert(FraudAlert a){return new AlertView(a.id(),a.transactionReference(),a.score(),a.band(),a.status(),a.createdAt());}
    private ScoreResponse toScore(String memberId,RiskAssessment a){
        try{List<RiskReason> reasons=json.readValue(a.reasonsJson(),new TypeReference<>(){});UUID alertId=alerts.findByMemberIdAndTransactionReference(memberId,a.transactionReference()).map(FraudAlert::id).orElse(null);return toScore(a,reasons,alertId);}
        catch(JsonProcessingException e){throw new IllegalStateException("Stored risk explanation is invalid",e);}
    }
    private ScoreResponse toScore(RiskAssessment a,List<RiskReason> reasons,UUID alertId){return new ScoreResponse(a.id(),a.score(),a.band(),a.recommendedAction(),a.enforcedAction(),a.modelVersion(),reasons,a.collectiveGroupSize(),a.collectiveRiskScore(),alertId,a.createdAt());}
    private FraudDecisionPolicy policy(String memberId){return policies.findByMemberId(memberId).orElseGet(()->policies.save(FraudDecisionPolicy.create(memberId)));}
    private DecisionPolicyResponse toPolicy(FraudDecisionPolicy p){return new DecisionPolicyResponse(p.mode(),p.challengeEnabled(),p.holdEnabled(),p.blockEnabled(),p.updatedAt());}
    private AssessmentSummary summary(RiskAssessment a){return new AssessmentSummary(a.id(),a.transactionReference(),a.score(),a.band(),a.recommendedAction(),a.enforcedAction(),a.createdAt());}
    private TopEntity topEntity(FraudEntityLink link){String hash=link.entityHash();return new TopEntity(link.entityType(),hash.substring(0,Math.min(12,hash.length())),link.observationCount());}
    private Map<String,Long> count(List<String> values){Map<String,Long> result=new LinkedHashMap<>();values.forEach(v->result.merge(v,1L,Long::sum));return Collections.unmodifiableMap(result);}
    private String probableType(List<String> codes){if(codes.stream().anyMatch(Set.of("NEW_DEVICE","NEW_LOCATION","BENEFICIARY_CHANGED","SESSION_RISK")::contains))return "ACCOUNT_TAKEOVER";if(codes.contains("COLLECTIVE_PATTERN"))return "FRAUD_RING";if(codes.contains("ML_ANOMALY")||codes.contains("BEHAVIORAL_DEVIATION"))return "BEHAVIORAL_ANOMALY";return codes.size()==1&&codes.contains("BASELINE")?"NONE":"TRANSACTION_FRAUD";}
    private void rejectRawCardNumber(String value){if(value!=null&&value.replaceAll("[ -]","").matches("[0-9]{12,19}"))throw new IllegalArgumentException("Raw card numbers are forbidden");}
    private FraudMonitoringSubject ensureSubject(String memberId,String sector,String type,String hash){return monitoringSubjects.findByMemberIdAndSectorIdAndSubjectTypeAndSubjectHash(memberId,sector,type,hash).orElseGet(()->{try{return monitoringSubjects.saveAndFlush(FraudMonitoringSubject.enroll(memberId,sector,type,hash));}catch(DataIntegrityViolationException race){return monitoringSubjects.findByMemberIdAndSectorIdAndSubjectTypeAndSubjectHash(memberId,sector,type,hash).orElseThrow(()->race);}});}
    private String enforcedAction(String memberId,String recommended){
        FraudDecisionPolicy p=policy(memberId);if("ALLOW".equals(recommended))return "ALLOW";if(!"ACTIVE_DECISION".equals(p.mode()))return "ALERT";
        return switch(recommended){case "CHALLENGE"->p.challengeEnabled()?"CHALLENGE":"ALERT";case "HOLD"->p.holdEnabled()?"HOLD":"ALERT";case "BLOCK"->p.blockEnabled()?"BLOCK":"ALERT";default->"ALERT";};
    }
}
