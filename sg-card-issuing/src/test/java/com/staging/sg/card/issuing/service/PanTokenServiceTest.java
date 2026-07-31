package com.staging.sg.card.issuing.service;

import com.staging.sg.card.issuing.repository.PaymentIdentifierRepository;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PanTokenServiceTest {
    @Test
    void tokenIsOpaqueAndPanNeverAppearsInItsValue() {
        PaymentIdentifierRepository repository =
                mock(PaymentIdentifierRepository.class);
        when(repository.existsByVaultReference(anyString())).thenReturn(false);
        PanTokenService service = new PanTokenService(repository);

        String token = service.newToken();

        assertTrue(token.startsWith("pan_tok_"));
        assertFalse(token.contains("5321960000003348"));
        assertEquals("532196******3348",
                PanTokenService.mask("5321960000003348"));
    }
}
