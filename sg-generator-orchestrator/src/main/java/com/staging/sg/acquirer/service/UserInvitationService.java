package com.staging.sg.acquirer.service;

import com.staging.sg.common.entity.*;
import com.staging.sg.common.repository.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;

@Service
public class UserInvitationService {
    private final UserRepository users;
    private final UserInvitationRepository invitations;
    private final PasswordEncoder passwords;
    private final SecureRandom random = new SecureRandom();

    public UserInvitationService(UserRepository users, UserInvitationRepository invitations,
            PasswordEncoder passwords) {
        this.users = users;
        this.invitations = invitations;
        this.passwords = passwords;
    }

    @Transactional
    public InvitationResult invite(String login, String email, String invitedBy) {
        if (blank(login) || blank(email) || !email.contains("@") || blank(invitedBy))
            throw new IllegalArgumentException("Invalid merchant invitation");
        if (users.existsByLogin(login)) throw new IllegalStateException("Login already exists");
        User user = new User();
        user.setLogin(login.trim().toLowerCase(Locale.ROOT));
        user.setEmail(email.trim().toLowerCase(Locale.ROOT));
        user.setRole("MERCHANT");
        user.setActive(false);
        user.setCreatedBy(invitedBy);
        byte[] unusablePassword = new byte[48];
        random.nextBytes(unusablePassword);
        user.setPassword(passwords.encode(Base64.getEncoder().encodeToString(unusablePassword)));
        User saved = users.save(user);

        byte[] tokenBytes = new byte[32];
        random.nextBytes(tokenBytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
        Instant expiresAt = Instant.now().plus(Duration.ofHours(48));
        UserInvitation invitation = invitations.save(UserInvitation.create(
                saved.getId(), sha256(rawToken), expiresAt, invitedBy));
        return new InvitationResult(saved.getId(), invitation.getId(), rawToken, expiresAt);
    }

    @Transactional
    public Long activate(String rawToken, String newPassword) {
        validatePassword(newPassword);
        UserInvitation invitation = invitations.findByTokenHash(sha256(rawToken))
                .orElseThrow(() -> new IllegalArgumentException("Invalid invitation token"));
        invitation.consume();
        User user = users.findById(invitation.getUserId())
                .orElseThrow(() -> new IllegalStateException("Invited user no longer exists"));
        user.setPassword(passwords.encode(newPassword));
        user.setActive(true);
        users.save(user);
        invitations.save(invitation);
        return user.getId();
    }

    private static void validatePassword(String value) {
        if (value == null || value.length() < 12 || !value.matches(".*[A-Z].*")
                || !value.matches(".*[a-z].*") || !value.matches(".*\\d.*")
                || !value.matches(".*[^A-Za-z0-9].*")) {
            throw new IllegalArgumentException("Password does not meet activation policy");
        }
    }
    private static String sha256(String value) {
        if (blank(value)) throw new IllegalArgumentException("Invitation token is required");
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
    private static boolean blank(String value) { return value == null || value.isBlank(); }

    public record InvitationResult(Long userId, UUID invitationId,
            String activationToken, Instant expiresAt) {}
}
