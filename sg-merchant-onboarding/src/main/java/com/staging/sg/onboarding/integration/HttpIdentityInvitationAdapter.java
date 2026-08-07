package com.staging.sg.onboarding.integration;

import com.staging.sg.onboarding.port.IdentityInvitationPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Optional;

@Component
public class HttpIdentityInvitationAdapter implements IdentityInvitationPort {
    private final boolean enabled;
    private final RestClient client;

    public HttpIdentityInvitationAdapter(
            @Value("${merchant-onboarding.identity.enabled:false}") boolean enabled,
            @Value("${merchant-onboarding.identity.base-url:http://127.0.0.1:8080}") String baseUrl) {
        this.enabled = enabled;
        this.client = RestClient.builder().baseUrl(baseUrl).build();
    }

    @Override
    public Optional<Invitation> invite(String login, String email, String bearerAuthorization) {
        if (!enabled) return Optional.empty();
        if (bearerAuthorization == null || !bearerAuthorization.startsWith("Bearer "))
            throw new IllegalStateException("Identity invitation requires the caller bearer token");
        Invitation result = client.post().uri("/api/onboarding/identity/invitations")
                .header("Authorization", bearerAuthorization)
                .body(new InvitationRequest(login, email))
                .retrieve().body(Invitation.class);
        if (result == null || result.userId() == null || result.activationToken() == null)
            throw new IllegalStateException("Identity returned an incomplete invitation");
        return Optional.of(result);
    }

    private record InvitationRequest(String login, String email) {}
}
