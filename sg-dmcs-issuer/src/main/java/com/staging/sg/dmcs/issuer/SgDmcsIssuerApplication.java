package com.staging.sg.dmcs.issuer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(exclude = {org.springframework.boot.autoconfigure.liquibase.LiquibaseAutoConfiguration.class})
@EntityScan(basePackages = {
    "com.staging.sg.common.entity"
})
@EnableJpaRepositories(basePackages = {
    "com.staging.sg.common.repository"
})
public class SgDmcsIssuerApplication {
    public static void main(String[] args) {
        SpringApplication.run(SgDmcsIssuerApplication.class, args);
    }
}
