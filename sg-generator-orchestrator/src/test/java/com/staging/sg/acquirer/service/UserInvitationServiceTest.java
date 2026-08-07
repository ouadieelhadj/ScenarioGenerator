package com.staging.sg.acquirer.service;

import com.staging.sg.common.entity.*;
import com.staging.sg.common.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserInvitationServiceTest {
    @Mock UserRepository users;
    @Mock UserInvitationRepository invitations;

    @Test
    void invitationStoresOnlyHashAndActivatesAccountOnce() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        UserInvitationService service = new UserInvitationService(users, invitations, encoder);
        when(users.existsByLogin("merchant.one")).thenReturn(false);
        when(users.save(any())).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            if (user.getId() == null) user.setId(42L);
            return user;
        });
        when(invitations.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.invite("merchant.one", "merchant@example.test", "commercial.user");

        assertThat(result.activationToken()).hasSizeGreaterThan(32);
        ArgumentCaptor<UserInvitation> invitation = ArgumentCaptor.forClass(UserInvitation.class);
        verify(invitations).save(invitation.capture());
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(users).save(userCaptor.capture());
        User user = userCaptor.getValue();
        assertThat(user.isActive()).isFalse();
        assertThat(user.getPassword()).doesNotContain(result.activationToken());

        when(invitations.findByTokenHash(anyString())).thenReturn(Optional.of(invitation.getValue()));
        when(users.findById(42L)).thenReturn(Optional.of(user));
        Long activatedId = service.activate(result.activationToken(), "StrongPassword1!");

        assertThat(activatedId).isEqualTo(42L);
        assertThat(user.isActive()).isTrue();
        assertThat(encoder.matches("StrongPassword1!", user.getPassword())).isTrue();
        assertThatThrownBy(() -> service.activate(result.activationToken(), "StrongPassword1!"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("already used");
    }
}
