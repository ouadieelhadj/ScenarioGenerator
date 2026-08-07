package com.staging.sg.switchlab.bff.api;

import com.staging.sg.switchlab.bff.config.SwitchLabCorrelationFilter;
import com.staging.sg.switchlab.bff.service.SwitchLabClearingService;
import com.staging.sg.switchlab.bff.service.SwitchLabGatewayService;
import com.staging.sg.switchlab.contracts.*;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/switchlab/v1/clearing")
public class SwitchLabClearingController {
    private final SwitchLabGatewayService authentication; private final SwitchLabClearingService service;
    public SwitchLabClearingController(SwitchLabGatewayService authentication,SwitchLabClearingService service){this.authentication=authentication;this.service=service;}
    @GetMapping("/networks") public List<SwitchLabClearingNetwork> networks(@RequestHeader(HttpHeaders.AUTHORIZATION)String authorization){requireAuthorized(authorization);return service.networks();}
    @GetMapping("/artifacts") public List<SwitchLabClearingArtifact> artifacts(@RequestHeader(HttpHeaders.AUTHORIZATION)String authorization){requireAuthorized(authorization);return service.artifacts();}
    @PostMapping(value="/networks/{code}/files",consumes="multipart/form-data")
    public SwitchLabClearingArtifact upload(@RequestHeader(HttpHeaders.AUTHORIZATION)String authorization,@PathVariable String code,@RequestPart("file")MultipartFile file,HttpServletRequest request)throws Exception{requireAuthorized(authorization);return service.upload(code,file,correlation(request));}
    @PostMapping("/eod") public SwitchLabClearingEodResult eod(@RequestHeader(HttpHeaders.AUTHORIZATION)String authorization,@RequestBody SwitchLabClearingEodRequest body,HttpServletRequest request){requireAuthorized(authorization);return service.eod(body,correlation(request));}
    private String correlation(HttpServletRequest request){return String.valueOf(request.getAttribute(SwitchLabCorrelationFilter.HEADER));}
    private void requireAuthorized(String authorization){if(!authentication.authorized(authorization))throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Invalid SwitchLab session");}
}
