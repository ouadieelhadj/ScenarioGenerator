package com.staging.sg.switchlab.bff.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.staging.sg.switchlab.bff.service.SwitchLabGatewayService;
import com.staging.sg.switchlab.bff.service.SwitchLabIndustrializationService;
import com.staging.sg.switchlab.contracts.SwitchLabIndustrialReadiness;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/switchlab/v1/industrialization")
public class SwitchLabIndustrializationController{
 private final SwitchLabGatewayService authentication;private final SwitchLabIndustrializationService service;private final ObjectMapper mapper;
 public SwitchLabIndustrializationController(SwitchLabGatewayService authentication,SwitchLabIndustrializationService service,ObjectMapper mapper){this.authentication=authentication;this.service=service;this.mapper=mapper;}
 @GetMapping("/readiness")public List<SwitchLabIndustrialReadiness> readiness(@RequestHeader(HttpHeaders.AUTHORIZATION)String authorization){requireAuthorized(authorization);return service.readiness();}
 @GetMapping("/backup")public ResponseEntity<byte[]> backup(@RequestHeader(HttpHeaders.AUTHORIZATION)String authorization)throws Exception{requireAuthorized(authorization);return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=\"switchlab-configuration-backup.json\"").body(mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(service.backup()));}
 private void requireAuthorized(String authorization){if(!authentication.authorized(authorization))throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Invalid SwitchLab session");}
}
