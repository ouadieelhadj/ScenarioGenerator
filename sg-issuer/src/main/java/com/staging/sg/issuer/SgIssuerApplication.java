package com.staging.sg.issuer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

@SpringBootApplication(scanBasePackages = {
    "com.staging.sg.issuer",
    "com.staging.sg.common"
})
public class SgIssuerApplication {

    private static final Logger log = LoggerFactory.getLogger(SgIssuerApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(SgIssuerApplication.class, args);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        log.info("================================================");
        log.info("  SG Issuer — started successfully");
        log.info("  Listening on port 8200");
        log.info("================================================");
    }
}
