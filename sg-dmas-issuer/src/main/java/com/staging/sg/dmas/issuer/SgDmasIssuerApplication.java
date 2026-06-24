package com.staging.sg.dmas.issuer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EntityScan(basePackages = "com.staging.sg.common.entity")
@EnableJpaRepositories(basePackages = "com.staging.sg.common.repository")
@ComponentScan(basePackages = {"com.staging.sg.dmas.issuer", "com.staging.sg.common"})
public class SgDmasIssuerApplication {
    public static void main(String[] args) {
        SpringApplication.run(SgDmasIssuerApplication.class, args);
    }
}
