package com.staging.sg.dmas.acquirer.api;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/whoami")
public class WhoAmIController {

    @GetMapping
    public ResponseEntity<?> whoami() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Map<String,Object> r = new LinkedHashMap<>();
        if (auth == null) {
            r.put("authenticated", false);
            r.put("note", "no authentication in context");
        } else {
            r.put("authenticated", auth.isAuthenticated());
            r.put("name", auth.getName());
            r.put("authorities", auth.getAuthorities().toString());
            r.put("principal_class", auth.getPrincipal().getClass().getSimpleName());
        }
        return ResponseEntity.ok(r);
    }
}
