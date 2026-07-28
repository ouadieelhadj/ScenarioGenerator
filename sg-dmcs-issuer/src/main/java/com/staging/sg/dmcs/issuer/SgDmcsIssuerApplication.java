package com.staging.sg.dmcs.issuer;

import com.staging.sg.common.persistence.DmcsIssuerPersistenceConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication(exclude = {org.springframework.boot.autoconfigure.liquibase.LiquibaseAutoConfiguration.class})
@Import(DmcsIssuerPersistenceConfiguration.class)
public class SgDmcsIssuerApplication {
    public static void main(String[] args) {
        SpringApplication.run(SgDmcsIssuerApplication.class, args);
    }
}
