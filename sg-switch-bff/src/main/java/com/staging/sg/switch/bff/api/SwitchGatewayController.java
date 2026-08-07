package com.staging.sg.member.bff.api;

import com.staging.sg.member.bff.service.SwitchGatewayService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
public class SwitchGatewayController{
 private final SwitchGatewayService gateway;public SwitchGatewayController(SwitchGatewayService gateway){this.gateway=gateway;}
 @RequestMapping(path={"/auth/login","/api/me/navigation","/api/admin/users","/api/admin/users/**","/api/admin/roles","/api/admin/roles/**","/api/admin/deployments","/api/admin/deployments/**"})
 public ResponseEntity<byte[]> forward(HttpServletRequest request,@RequestHeader HttpHeaders headers,@RequestBody(required=false)byte[] body){if(!"/auth/login".equals(request.getRequestURI())&&!gateway.authorized(headers.getFirst(HttpHeaders.AUTHORIZATION)))return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new byte[0]);return gateway.forward(request.getRequestURI(),request.getQueryString(),HttpMethod.valueOf(request.getMethod()),headers,body);}
}
