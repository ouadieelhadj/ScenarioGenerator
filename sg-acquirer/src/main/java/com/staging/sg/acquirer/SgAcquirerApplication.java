package com.staging.sg.acquirer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.event.EventListener;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@ComponentScan(basePackages = {
    "com.staging.sg.acquirer",
    "com.staging.sg.common"
})
@EntityScan(basePackages = {
    "com.staging.sg.common.entity"
})
@EnableJpaRepositories(basePackages = {
    "com.staging.sg.common.repository"
})
public class SgAcquirerApplication {

    private static final Logger log = LoggerFactory.getLogger(SgAcquirerApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(SgAcquirerApplication.class, args);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        log.info("================================================");
        log.info("  SG Acquirer — started successfully");
        log.info("  API    : http://localhost:8080/api/status");
        log.info("================================================");
    }
}
