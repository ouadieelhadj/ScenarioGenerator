package com.staging.sg.fraud.api;

import com.staging.sg.fraud.api.FraudGovernanceApi.*;
import com.staging.sg.fraud.security.MemberContext;
import com.staging.sg.fraud.service.FraudGovernanceService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/fraud/v1/admin/governance")
public class FraudGovernanceController {
    private final FraudGovernanceService governance;private final MemberContext members;
    public FraudGovernanceController(FraudGovernanceService governance,MemberContext members){this.governance=governance;this.members=members;}
    @GetMapping("/graph/{sectorId}")public GraphPolicyResponse graph(Authentication auth,@PathVariable String sectorId){return governance.graph(members.requireMemberId(auth),sectorId);}
    @PutMapping("/graph/{sectorId}")public GraphPolicyResponse graph(Authentication auth,@PathVariable String sectorId,@Valid@RequestBody GraphPolicyRequest request){return governance.updateGraph(members.requireMemberId(auth),sectorId,request);}
    @GetMapping("/ai/{sectorId}")public AiPolicyResponse ai(Authentication auth,@PathVariable String sectorId){return governance.ai(members.requireMemberId(auth),sectorId);}
    @PutMapping("/ai/{sectorId}")public AiPolicyResponse ai(Authentication auth,@PathVariable String sectorId,@Valid@RequestBody AiPolicyRequest request){return governance.updateAi(members.requireMemberId(auth),sectorId,request);}
}
