package com.staging.sg.mc.sms.acquirer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Mastercard SMS — Acquirer (Membre).
 * Port REST  : 8095
 * Port ISO   : 8096 (ecoute des messages entrants)
 */
@SpringBootApplication(scanBasePackages = {
        "com.staging.sg.mc.sms.acquirer",
        "com.staging.sg.common"
})
@EntityScan(basePackages = {
        "com.staging.sg.mc.sms.acquirer.entity",
        "com.staging.sg.common.entity"
})
@EnableJpaRepositories(basePackages = {
        "com.staging.sg.mc.sms.acquirer.repository",
        "com.staging.sg.common.repository"
})
public class SgMcSmsAcquirerApplication {
    public static void main(String[] args) {
        SpringApplication.run(SgMcSmsAcquirerApplication.class, args);
    }
}
