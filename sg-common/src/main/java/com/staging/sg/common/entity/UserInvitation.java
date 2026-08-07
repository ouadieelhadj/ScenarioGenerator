package com.staging.sg.common.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_invitation", uniqueConstraints =
        @UniqueConstraint(name = "uk_user_invitation_token", columnNames = "token_hash"))
public class UserInvitation {
    @Id
    private UUID id;
    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;
    @Column(name = "token_hash", nullable = false, length = 64, updatable = false)
    private String tokenHash;
    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;
    @Column(name = "invited_by", nullable = false, length = 96, updatable = false)
    private String invitedBy;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "used_at")
    private Instant usedAt;

    protected UserInvitation() {}

    public static UserInvitation create(Long userId, String tokenHash,
            Instant expiresAt, String invitedBy) {
        if (userId == null || tokenHash == null || !tokenHash.matches("[0-9a-f]{64}")
                || expiresAt == null || !expiresAt.isAfter(Instant.now())
                || invitedBy == null || invitedBy.isBlank()) {
            throw new IllegalArgumentException("Invalid user invitation");
        }
        UserInvitation value = new UserInvitation();
        value.id = UUID.randomUUID();
        value.userId = userId;
        value.tokenHash = tokenHash;
        value.expiresAt = expiresAt;
        value.invitedBy = invitedBy;
        value.createdAt = Instant.now();
        return value;
    }

    public void consume() {
        if (usedAt != null) throw new IllegalStateException("Invitation already used");
        if (!expiresAt.isAfter(Instant.now())) throw new IllegalStateException("Invitation expired");
        usedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public Long getUserId() { return userId; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getUsedAt() { return usedAt; }
}
