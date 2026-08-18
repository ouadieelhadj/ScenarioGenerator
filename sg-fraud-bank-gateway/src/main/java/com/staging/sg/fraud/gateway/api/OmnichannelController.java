package com.staging.sg.fraud.gateway.api;

import com.staging.sg.fraud.gateway.api.IsoFraudApi.GatewayDecisionResponse;
import com.staging.sg.fraud.gateway.api.OmnichannelApi.*;
import com.staging.sg.fraud.gateway.service.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/fraud-gateway/v1")
public class OmnichannelController {
    private final OmnichannelGatewayService gateway; private final Iso20022FraudMapper iso20022;
    public OmnichannelController(OmnichannelGatewayService gateway,Iso20022FraudMapper iso20022){this.gateway=gateway;this.iso20022=iso20022;}
    @PostMapping("/events/universal:evaluate")
    public GatewayDecisionResponse universal(@RequestHeader(HttpHeaders.AUTHORIZATION)String authorization,@Valid @RequestBody UniversalTransactionRequest request){return gateway.evaluate(authorization,request);}
    @PostMapping("/iso20022/evaluate")
    public GatewayDecisionResponse iso20022(@RequestHeader(HttpHeaders.AUTHORIZATION)String authorization,@Valid @RequestBody Iso20022EvaluationRequest request){return gateway.evaluate(authorization,iso20022.toUniversal(request));}
    @PostMapping("/webhooks/{source}:evaluate")
    public GatewayDecisionResponse webhook(@RequestHeader(HttpHeaders.AUTHORIZATION)String authorization,@PathVariable String source,@Valid @RequestBody UniversalTransactionRequest request){return gateway.evaluate(authorization,request);}
    @PostMapping("/files/batches:evaluate")
    public BatchEvaluationResponse batch(@RequestHeader(HttpHeaders.AUTHORIZATION)String authorization,@Valid @RequestBody BatchEvaluationRequest request){return gateway.evaluateBatch(authorization,request);}
}
