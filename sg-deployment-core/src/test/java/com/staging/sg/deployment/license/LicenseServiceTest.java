package com.staging.sg.deployment.license;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LicenseServiceTest {
    @TempDir Path temp;

    @Test
    void signsVerifiesAndRendersLocalTestLicense() throws Exception {
        LicenseService service = new LicenseService();
        var pair = service.generateKeyPair();
        Path privateKey = temp.resolve("private.pem");
        Path publicKey = temp.resolve("public.pem");
        Path signed = temp.resolve("license.json.sig");
        Path pdf = temp.resolve("license.pdf");
        service.writePrivateKey(privateKey, pair.getPrivate());
        service.writePublicKey(publicKey, pair.getPublic());
        TechnicalLicense license = license();

        service.issue(license, service.readPrivateKey(privateKey), signed);
        TechnicalLicense verified = service.verify(signed, publicKey);
        new LicensePdfService().generate(verified, signed, pdf);

        assertEquals(license.clientCode(), verified.clientCode());
        assertTrue(Files.size(pdf) > 1_000);
        assertTrue(Files.readString(signed).contains("SHA256withRSA"));

        String tampered = Files.readString(signed).replaceFirst("payload", "payloadX");
        Files.writeString(signed, tampered);
        assertThrows(Exception.class, () -> service.verify(signed, publicKey));
    }

    private static TechnicalLicense license() {
        LocalDate today = LocalDate.now();
        return new TechnicalLicense(UUID.randomUUID().toString(), "LOCAL_TEST_BANK",
                "Banque Test Locale", "LOCAL", Instant.now().toString(),
                today.minusDays(1).toString(), today.plusDays(30).toString(),
                List.of("CARD_ISSUING"), List.of("MERCHANT_SITE_SIMULATOR"),
                "1.0.0-SNAPSHOT", "checker-local", true);
    }
}
