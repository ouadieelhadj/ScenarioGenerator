package com.staging.sg.fraud.gateway.api;
import com.staging.sg.fraud.gateway.api.IsoFraudApi.*;import com.staging.sg.fraud.gateway.service.*;import jakarta.validation.Valid;import org.springframework.http.HttpHeaders;import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/fraud-gateway/v1") public class IsoFraudController{private final Iso8583FraudMapper mapper;private final FraudPlatformClient platform;public IsoFraudController(Iso8583FraudMapper m,FraudPlatformClient p){mapper=m;platform=p;}
 @GetMapping("/health")public Health health(){return new Health("UP","ALERT_ONLY");}
 @PostMapping("/iso8583/evaluate")public IsoMessageResponse evaluate(@RequestHeader(HttpHeaders.AUTHORIZATION)String authorization,@Valid @RequestBody IsoMessageRequest request){var canonical=mapper.toCanonical(request);var score=platform.score(authorization,canonical);return mapper.response(request,score);}}
