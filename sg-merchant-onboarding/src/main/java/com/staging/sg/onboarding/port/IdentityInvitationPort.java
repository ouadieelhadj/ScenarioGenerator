package com.staging.sg.onboarding.port;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface IdentityInvitationPort {
    Optional<Invitation> invite(String login, String email, String bearerAuthorization);

    record Invitation(Long userId, UUID invitationId, String activationToken, Instant expiresAt) {}
}
