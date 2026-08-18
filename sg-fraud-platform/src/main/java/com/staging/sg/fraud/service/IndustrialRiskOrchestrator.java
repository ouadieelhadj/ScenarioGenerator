package com.staging.sg.fraud.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.staging.sg.fraud.api.FraudApi.ScoreRequest;
import com.staging.sg.fraud.domain.FraudAiPolicy;
import com.staging.sg.fraud.repository.FraudAiPolicyRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Optional V3 adapters. The deterministic engine remains authoritative when an
 * external feature or model service is disabled or temporarily unavailable.
 */
@Component
public class IndustrialRiskOrchestrator {
    static final List<String> FEATURE_NAMES=List.of("amount_deviation","attempts_last_hour","device_novelty",
            "location_novelty","beneficiary_age_minutes","graph_group_size","behavioral_deviation","threat_intelligence_signal");
    private final boolean featureEnabled,modelEnabled;private final URI feastUri,modelUri;private final String featureService,modelName;
    private final long amountBaselineMinor;private final Duration timeout;private final ObjectMapper json;private final HttpClient http;private final FraudAiPolicyRepository policies;
    private final AtomicReference<String> featureStatus=new AtomicReference<>("DISABLED");
    private final AtomicReference<String> modelStatus=new AtomicReference<>("DISABLED");

    public IndustrialRiskOrchestrator(ObjectMapper json,
            @Value("${fraud.integrations.feature-store.enabled:false}")boolean featureEnabled,
            @Value("${fraud.integrations.feature-store.url:http://127.0.0.1:6566}")String feastUrl,
            @Value("${fraud.integrations.feature-store.feature-service:fraud_scoring_v1}")String featureService,
            @Value("${fraud.integrations.model-inference.enabled:false}")boolean modelEnabled,
            @Value("${fraud.integrations.model-inference.url:http://127.0.0.1:5001/invocations}")String modelUrl,
            @Value("${fraud.integrations.model-inference.model-name:fraud-risk}")String modelName,
            @Value("${fraud.integrations.model-inference.amount-baseline-minor:250000}")long amountBaselineMinor,
            @Value("${fraud.integrations.timeout-ms:1500}")long timeoutMs,FraudAiPolicyRepository policies){
        this.json=json;this.featureEnabled=featureEnabled;this.feastUri=URI.create(trim(feastUrl)+"/get-online-features");
        this.featureService=featureService;this.modelEnabled=modelEnabled;this.modelUri=URI.create(modelUrl);this.modelName=modelName;
        this.amountBaselineMinor=Math.max(1,amountBaselineMinor);this.timeout=Duration.ofMillis(Math.max(100,timeoutMs));
        this.http=HttpClient.newBuilder().connectTimeout(this.timeout).build();this.policies=policies;
    }

    public Evaluation evaluate(String memberId,String subjectHash,ScoreRequest request,FraudCollectiveGraph.Result collective){
        String sectorId=FraudSector.normalize(request.sector(),request.channel());FraudAiPolicy policy=policies.findByMemberIdAndSectorId(memberId,sectorId).orElseGet(()->FraudAiPolicy.defaults(memberId,sectorId,modelName));
        Map<String,Double> features=baseFeatures(request,collective);String featureSource="REQUEST_AND_LOCAL_GRAPH";
        if(featureEnabled){try{features.putAll(fetchFeatures(memberId,sectorId,subjectHash));featureSource="FEAST_HTTP:"+featureService;featureStatus.set("UP");}
            catch(Exception failure){featureStatus.set("DEGRADED:"+failure.getClass().getSimpleName());}}
        if(!modelEnabled||!policy.enabled())return baseline(features,featureSource,policy,modelEnabled?"POLICY_DISABLED":"DISABLED");
        if("DRIFTED".equals(policy.driftStatus()))return baseline(features,featureSource,policy,"FALLBACK:DRIFTED");
        try{
            ModelResult champion=infer(features,policy.championModel());
            if(champion.driftScore()>policy.driftThreshold())return baseline(features,featureSource,policy,"FALLBACK:DRIFT_THRESHOLD");
            if(policy.explainabilityRequired()&&champion.explanation().isBlank())return baseline(features,featureSource,policy,"FALLBACK:MISSING_EXPLANATION");
            int challengerScore=-1;String challengerVersion="";
            if(useChallenger(request.transactionReference(),policy)){ModelResult challenger=infer(features,policy.challengerModel());challengerScore=challenger.score();challengerVersion=policy.challengerModel();}
            modelStatus.set("UP:"+policy.governanceMode());boolean active="ACTIVE".equals(policy.governanceMode());
            return new Evaluation(Map.copyOf(features),featureSource,active?champion.score():-1,active?champion.action():"",
                    active?policy.championModel():"risk-intelligence-v2-deterministic",modelStatus.get(),champion.score(),
                    challengerScore,challengerVersion,policy.governanceMode(),false,champion.explanation(),true,
                    policy.alertThreshold(),policy.challengeThreshold(),policy.holdThreshold(),policy.blockThreshold());
        }catch(Exception failure){return baseline(features,featureSource,policy,"FALLBACK:"+failure.getClass().getSimpleName());}
    }

    public String featureStatus(){return featureEnabled?featureStatus.get():"DISABLED";}
    public String modelStatus(){return modelEnabled?modelStatus.get():"DISABLED";}
    public boolean featureEnabled(){return featureEnabled;}public boolean modelEnabled(){return modelEnabled;}

    private Map<String,Double> baseFeatures(ScoreRequest request,FraudCollectiveGraph.Result collective){
        Map<String,Double> values=new LinkedHashMap<>();
        values.put("amount_deviation",Math.min(20d,request.amountMinor()/(double)amountBaselineMinor));
        values.put("attempts_last_hour",(double)request.attemptsLastHour());
        values.put("device_novelty",signal(request,"NEW_DEVICE")?1d:0d);
        values.put("location_novelty",signal(request,"NEW_LOCATION")?1d:0d);
        values.put("beneficiary_age_minutes",signal(request,"BENEFICIARY_CHANGED")?5d:10080d);
        values.put("graph_group_size",(double)collective.groupSize());
        values.put("behavioral_deviation",signal(request,"BEHAVIORAL_DEVIATION")?1d:0d);
        values.put("threat_intelligence_signal",signal(request,"THREAT_INTELLIGENCE")?1d:0d);
        return values;
    }

    private Map<String,Double> fetchFeatures(String memberId,String sectorId,String tokenHash)throws Exception{
        List<String> requested=FEATURE_NAMES.stream().map(name->"fraud_transaction_features:"+name).toList();
        Map<String,Object> body=Map.of("features",requested,"entities",Map.of("member_id",List.of(memberId),"sector_id",List.of(sectorId),"instrument_id",List.of(tokenHash)));
        JsonNode response=post(feastUri,body);JsonNode names=response.path("metadata").path("feature_names");JsonNode results=response.path("results");
        Map<String,Double> values=new LinkedHashMap<>();
        for(int i=0;i<Math.min(names.size(),results.size());i++){JsonNode value=results.get(i).path("values").path(0);if(value.isNumber())values.put(names.get(i).asText(),value.asDouble());}
        return values;
    }

    private ModelResult infer(Map<String,Double> features,String selectedModel)throws Exception{
        List<Double> row=FEATURE_NAMES.stream().map(features::get).toList();
        JsonNode response=post(modelUri,Map.of("dataframe_split",Map.of("columns",FEATURE_NAMES,"data",List.of(row))),selectedModel);
        JsonNode prediction=response.path("predictions").path(0);int score=prediction.path("riskScore").asInt(-1);
        if(score<0&&prediction.has("riskProbability"))score=(int)Math.round(prediction.path("riskProbability").asDouble()*1000d);
        if(score<0||score>1000)throw new IllegalStateException("Invalid model score");
        String explanation=prediction.path("explanation").asText("");
        if(explanation.isBlank()&&prediction.path("topFactors").isArray())explanation=prediction.path("topFactors").toString();
        return new ModelResult(score,prediction.path("recommendedAction").asText(action(score)),sanitize(explanation),prediction.path("driftScore").asDouble(0d));
    }

    private JsonNode post(URI uri,Object body)throws Exception{
        return post(uri,body,null);
    }
    private JsonNode post(URI uri,Object body,String selectedModel)throws Exception{
        HttpRequest.Builder builder=HttpRequest.newBuilder(uri).timeout(timeout).header("Content-Type","application/json");
        if(selectedModel!=null&&!selectedModel.isBlank())builder.header("X-Fraud-Model-Name",selectedModel);
        HttpRequest request=builder.POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build();
        HttpResponse<String> response=http.send(request,HttpResponse.BodyHandlers.ofString());
        if(response.statusCode()<200||response.statusCode()>=300)throw new IllegalStateException("HTTP "+response.statusCode());
        return json.readTree(response.body());
    }
    private boolean signal(ScoreRequest request,String code){return Boolean.TRUE.equals(request.observedSignals().get(code));}
    private String action(int score){return score>=900?"BLOCK":score>=800?"HOLD":score>=650?"CHALLENGE":score>=350?"ALERT":"ALLOW";}
    private String sanitize(String value){String safe=value.replaceAll("[\\r\\n\\t]+"," ").replaceAll("(?<![A-Za-z0-9])[0-9][0-9 -]{10,22}[0-9](?![A-Za-z0-9])","[REDACTED]").trim();return safe.substring(0,Math.min(500,safe.length()));}
    private static String trim(String value){return value.endsWith("/")?value.substring(0,value.length()-1):value;}
    private boolean useChallenger(String transactionReference,FraudAiPolicy policy){return policy.challengerModel()!=null&&policy.challengerTrafficPercent()>0&&Math.floorMod(transactionReference.hashCode(),100)<policy.challengerTrafficPercent();}
    private Evaluation baseline(Map<String,Double> features,String featureSource,FraudAiPolicy policy,String status){modelStatus.set(status);return new Evaluation(Map.copyOf(features),featureSource,-1,"","risk-intelligence-v2-deterministic",status,-1,-1,"",policy.governanceMode(),true,"Moteur déterministe utilisé",policy.enabled(),policy.alertThreshold(),policy.challengeThreshold(),policy.holdThreshold(),policy.blockThreshold());}
    private record ModelResult(int score,String action,String explanation,double driftScore){}
    public record Evaluation(Map<String,Double> features,String featureSource,int modelScore,String modelAction,
            String modelVersion,String modelStatus,int championShadowScore,int challengerShadowScore,String challengerModelVersion,
            String governanceMode,boolean fallbackApplied,String explanation,boolean policyEnabled,int alertThreshold,
            int challengeThreshold,int holdThreshold,int blockThreshold){public boolean hasModel(){return modelScore>=0;}public String action(int score){return score>=blockThreshold?"BLOCK":score>=holdThreshold?"HOLD":score>=challengeThreshold?"CHALLENGE":score>=alertThreshold?"ALERT":"ALLOW";}}
}
