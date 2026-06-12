package com.staging.sg;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

@SpringBootApplication
public class ScenarioGeneratorApplication {

    private static final Logger log = LoggerFactory.getLogger(ScenarioGeneratorApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(ScenarioGeneratorApplication.class, args);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        log.info("================================================");
        log.info("  ScenarioGenerator — started successfully");
        log.info("  API    : http://localhost:8080/api/status");
        log.info("  Issuer : port 8200");
        log.info("================================================");
    }
}
