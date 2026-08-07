package com.staging.sg.member.bff.api;

import com.staging.sg.member.bff.service.SwitchGatewayService;
import com.staging.sg.member.bff.service.SwitchInterfaceService;
import com.staging.sg.member.contracts.*;
import java.util.Arrays;
import java.util.List;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/switch/v1/interfaces")
public class SwitchInterfaceController{
 private final SwitchGatewayService gateway;private final SwitchInterfaceService service;public SwitchInterfaceController(SwitchGatewayService gateway,SwitchInterfaceService service){this.gateway=gateway;this.service=service;}
 @GetMapping("/capabilities")public SwitchInterfaceCapability capability(@RequestHeader(HttpHeaders.AUTHORIZATION)String authorization){authorized(authorization);return service.capability(authorization);}
 @GetMapping public List<SwitchInterfaceDefinition> interfaces(@RequestHeader(HttpHeaders.AUTHORIZATION)String authorization){authorized(authorization);return Arrays.asList(service.interfaces(authorization));}
 private void authorized(String authorization){if(!gateway.authorized(authorization))throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Invalid Switch session");}
}
