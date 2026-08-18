package com.staging.sg.fraud.api;

import com.staging.sg.fraud.api.CommercialRiskApi.*;
import com.staging.sg.fraud.security.MemberContext;
import com.staging.sg.fraud.service.CommercialRiskService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1")
public class CommercialRiskController {
    private final CommercialRiskService service; private final MemberContext members;
    public CommercialRiskController(CommercialRiskService service,MemberContext members){this.service=service;this.members=members;}
    @PostMapping("/risk/score")
    public RiskScoreResponse score(Authentication authentication,@Valid @RequestBody RiskScoreRequest request){
        return service.score(members.requireMemberId(authentication),request);
    }
}
