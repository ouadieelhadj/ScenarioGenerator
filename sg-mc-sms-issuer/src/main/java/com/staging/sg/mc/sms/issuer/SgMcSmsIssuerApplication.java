package com.staging.sg.mc.sms.issuer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Mastercard SMS — Issuer (Simulateur Mastercard).
 * Port REST  : 8097
 * Port ISO   : 8098 (ecoute des 0200 de l'acquereur)
 */
@SpringBootApplication(scanBasePackages = {
        "com.staging.sg.mc.sms.issuer",
        "com.staging.sg.common"
})
public class SgMcSmsIssuerApplication {
    public static void main(String[] args) {
        SpringApplication.run(SgMcSmsIssuerApplication.class, args);
    }
}
