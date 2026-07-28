package com.staging.sg.mc.sms.acquirer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Mastercard SMS — Acquirer (Membre).
 * Port REST  : 8095
 * Port ISO   : 8096 (ecoute des messages entrants)
 */
@SpringBootApplication(scanBasePackages = {
        "com.staging.sg.mc.sms.acquirer",
        "com.staging.sg.common"
})
public class SgMcSmsAcquirerApplication {
    public static void main(String[] args) {
        SpringApplication.run(SgMcSmsAcquirerApplication.class, args);
    }
}
