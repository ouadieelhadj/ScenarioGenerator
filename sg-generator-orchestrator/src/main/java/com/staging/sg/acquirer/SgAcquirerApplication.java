package com.staging.sg.acquirer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
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

    private final Environment env;

    public SgAcquirerApplication(Environment env) {
        this.env = env;
    }

    public static void main(String[] args) {
        SpringApplication.run(SgAcquirerApplication.class, args);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        // Lit le port reel depuis l'environnement (resolu : argument --server.port prioritaire sur le yml)
        String port = env.getProperty("local.server.port",
                        env.getProperty("server.port", "8080"));
        log.info("================================================");
        log.info("  SG Acquirer — started successfully");
        log.info("  API    : http://localhost:{}/api/status", port);
        log.info("================================================");
    }
}

