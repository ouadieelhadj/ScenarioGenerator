package com.staging.sg.card.issuing.service;

import com.staging.sg.card.issuing.repository.PaymentIdentifierRepository;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Base64;

@Service
public class PanTokenService {
    private final PaymentIdentifierRepository identifiers;
    private final SecureRandom random = new SecureRandom();

    public PanTokenService(PaymentIdentifierRepository identifiers) {
        this.identifiers = identifiers;
    }

    public String newToken() {
        for (int attempt = 0; attempt < 10; attempt++) {
            byte[] entropy = new byte[24];
            random.nextBytes(entropy);
            String token = "pan_tok_" + Base64.getUrlEncoder()
                    .withoutPadding().encodeToString(entropy);
            if (!identifiers.existsByVaultReference(token)) return token;
        }
        throw new IllegalStateException("Cannot allocate a unique PAN token");
    }

    public static String mask(String pan) {
        if (pan == null || !pan.matches("\\d{12,19}")) {
            throw new IllegalArgumentException("PAN must contain 12 to 19 digits");
        }
        return pan.substring(0, 6)
                + "*".repeat(pan.length() - 10)
                + pan.substring(pan.length() - 4);
    }
}
