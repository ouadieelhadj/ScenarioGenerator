package com.staging.sg.dmcs.acquirer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.liquibase.LiquibaseAutoConfiguration;
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
public class SgDmcsAcquirerApplication {
    public static void main(String[] args) {
        SpringApplication.run(SgDmcsAcquirerApplication.class, args);
    }
}
