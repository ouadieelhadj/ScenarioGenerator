package com.staging.sg.acquirer.api;

import com.staging.sg.acquirer.service.UserNavigationService;
import com.staging.sg.acquirer.service.UserNavigationService.NavigationResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me")
public class MeController {

    private final UserNavigationService navigationService;

    public MeController(UserNavigationService navigationService) {
        this.navigationService = navigationService;
    }

    @GetMapping("/navigation")
    public ResponseEntity<NavigationResponse> navigation(Authentication authentication) {
        return ResponseEntity.ok(navigationService.forLogin(authentication.getName()));
    }
}
