package com.staging.sg.fraud.api;

import com.staging.sg.fraud.api.FraudApi.*;
import com.staging.sg.fraud.security.MemberContext;
import com.staging.sg.fraud.service.FraudService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController @RequestMapping("/api/fraud/v1")
public class FraudController {
    private final FraudService service; private final MemberContext members;
    public FraudController(FraudService service,MemberContext members){this.service=service;this.members=members;}
    @GetMapping("/health") public Health health(){return new Health("UP","ALERT_ONLY");}
    @GetMapping("/capabilities") public Capabilities capabilities(){return new Capabilities("GOVERNED",true,true,true,true,true,true,true,true,true,true,true,true,true,true,true,true);}
    @PostMapping("/cards/monitoring-enrollments") @ResponseStatus(HttpStatus.CREATED)
    public EnrollmentResponse enroll(Authentication auth,@Valid @RequestBody EnrollmentRequest request){return service.enroll(members.requireMemberId(auth),request);}
    @PostMapping("/risk/transactions:score")
    public ScoreResponse score(Authentication auth,@Valid @RequestBody ScoreRequest request){return service.score(members.requireMemberId(auth),request);}
    @GetMapping("/alerts") public List<AlertView> alerts(Authentication auth){return service.listAlerts(members.requireMemberId(auth));}
    @PostMapping("/alerts/{alertId}/feedback") @ResponseStatus(HttpStatus.CREATED)
    public FeedbackResponse feedback(Authentication auth,@PathVariable UUID alertId,@Valid @RequestBody FeedbackRequest request){return service.feedback(members.requireMemberId(auth),alertId,request);}
    @PostMapping("/threat-signals") @ResponseStatus(HttpStatus.CREATED)
    public ThreatSignalResponse threatSignal(Authentication auth,@Valid @RequestBody ThreatSignalRequest request){return service.addThreatSignal(members.requireMemberId(auth),request);}
    @PostMapping("/cases") @ResponseStatus(HttpStatus.CREATED)
    public CaseView openCase(Authentication auth,@Valid @RequestBody CaseRequest request){return service.openCase(members.requireMemberId(auth),request);}
    @GetMapping("/cases") public List<CaseView> cases(Authentication auth){return service.listCases(members.requireMemberId(auth));}
    @PostMapping("/controls/candidates/backtests") @ResponseStatus(HttpStatus.CREATED)
    public ControlBacktestResponse backtest(Authentication auth,@Valid @RequestBody ControlBacktestRequest request){return service.backtest(members.requireMemberId(auth),request);}
    @PostMapping("/lab/batches:score")
    public BatchScoreResponse batch(Authentication auth,@Valid @RequestBody BatchScoreRequest request){return service.batchScore(members.requireMemberId(auth),request);}
    @GetMapping("/decision-policy") public DecisionPolicyResponse policy(Authentication auth){return service.getPolicy(members.requireMemberId(auth));}
    @PutMapping("/decision-policy") public DecisionPolicyResponse policy(Authentication auth,@Valid @RequestBody DecisionPolicyRequest request){return service.updatePolicy(members.requireMemberId(auth),request);}
}
