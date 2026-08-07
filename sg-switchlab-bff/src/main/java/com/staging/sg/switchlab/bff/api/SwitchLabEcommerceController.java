package com.staging.sg.switchlab.bff.api;

import com.staging.sg.switchlab.bff.service.SwitchLabEcommerceService;
import com.staging.sg.switchlab.bff.service.SwitchLabGatewayService;
import com.staging.sg.switchlab.contracts.SwitchLabEcommerceComponent;
import com.staging.sg.switchlab.contracts.SwitchLabEcommerceScenario;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/switchlab/v1/ecommerce")
public class SwitchLabEcommerceController{
 private final SwitchLabGatewayService authentication;private final SwitchLabEcommerceService service;
 public SwitchLabEcommerceController(SwitchLabGatewayService authentication,SwitchLabEcommerceService service){this.authentication=authentication;this.service=service;}
 @GetMapping("/components")public List<SwitchLabEcommerceComponent> components(@RequestHeader(HttpHeaders.AUTHORIZATION)String authorization){requireAuthorized(authorization);return service.components();}
 @GetMapping("/scenarios")public List<SwitchLabEcommerceScenario> scenarios(@RequestHeader(HttpHeaders.AUTHORIZATION)String authorization){requireAuthorized(authorization);return service.scenarios();}
 private void requireAuthorized(String authorization){if(!authentication.authorized(authorization))throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Invalid SwitchLab session");}
}
