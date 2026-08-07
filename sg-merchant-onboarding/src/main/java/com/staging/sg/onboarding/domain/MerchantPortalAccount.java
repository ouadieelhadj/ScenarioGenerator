package com.staging.sg.onboarding.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Entity
@Table(name = "merchant_portal_account", uniqueConstraints = {
        @UniqueConstraint(name = "uk_merchant_portal_account_login", columnNames = "login"),
        @UniqueConstraint(name = "uk_merchant_portal_account_email", columnNames = "email")
})
public class MerchantPortalAccount {
    @Id
    private UUID id;
    @Column(nullable = false, length = 96, updatable = false)
    private String login;
    @Column(nullable = false, length = 254, updatable = false)
    private String email;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AccountStatus status;
    @Column(name = "created_by_commercial", nullable = false, length = 96, updatable = false)
    private String createdByCommercial;
    @Column(name = "identity_user_id", length = 96)
    private String identityUserId;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Version
    private long version;

    protected MerchantPortalAccount() {}

    public static MerchantPortalAccount invite(String login, String email, String commercial) {
        requireText(login, "login");
        requireText(email, "email");
        requireText(commercial, "commercial");
        if (!email.contains("@")) throw new IllegalArgumentException("Invalid email");
        MerchantPortalAccount account = new MerchantPortalAccount();
        account.id = UUID.randomUUID();
        account.login = login.trim().toLowerCase(Locale.ROOT);
        account.email = email.trim().toLowerCase(Locale.ROOT);
        account.status = AccountStatus.INVITATION_PENDING;
        account.createdByCommercial = commercial.trim();
        account.createdAt = Instant.now();
        return account;
    }

    public void linkIdentity(String identityUserId) {
        requireText(identityUserId, "identityUserId");
        this.identityUserId = identityUserId.trim();
        this.status = AccountStatus.ACTIVE;
    }

    public void registerPendingIdentity(String identityUserId) {
        requireText(identityUserId, "identityUserId");
        this.identityUserId = identityUserId.trim();
    }

    static void requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
    }

    public UUID id() { return id; }
    public String login() { return login; }
    public String email() { return email; }
    public AccountStatus status() { return status; }
    public String createdByCommercial() { return createdByCommercial; }
    public String identityUserId() { return identityUserId; }
}
