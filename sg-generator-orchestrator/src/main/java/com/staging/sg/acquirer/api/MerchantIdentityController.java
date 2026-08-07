package com.staging.sg.acquirer.api;

import com.staging.sg.acquirer.service.UserInvitationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

@RestController
public class MerchantIdentityController {
    private final UserInvitationService service;

    public MerchantIdentityController(UserInvitationService service) { this.service = service; }

    @PostMapping("/api/onboarding/identity/invitations")
    @PreAuthorize("hasAnyRole('ADMIN','COMMERCIAL') or hasAuthority('ONBOARDING_PROSPECT_CREATE')")
    public InvitationResponse invite(@Valid @RequestBody InvitationRequest request,
            Authentication authentication) {
        var value = service.invite(request.login(), request.email(), authentication.getName());
        return new InvitationResponse(value.userId(), value.invitationId(),
                value.activationToken(), value.expiresAt());
    }

    @PostMapping("/auth/merchant-invitations/activate")
    public ActivationResponse activate(@Valid @RequestBody ActivationRequest request) {
        return new ActivationResponse(service.activate(request.token(), request.password()), "ACTIVE");
    }

    public record InvitationRequest(@NotBlank String login, @Email @NotBlank String email) {}
    public record InvitationResponse(Long userId, UUID invitationId,
            String activationToken, Instant expiresAt) {}
    public record ActivationRequest(@NotBlank String token, @NotBlank String password) {}
    public record ActivationResponse(Long userId, String status) {}
}
