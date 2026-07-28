package com.staging.sg.dmcs.acquirer;

import com.staging.sg.common.persistence.DmcsAcquirerPersistenceConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.liquibase.LiquibaseAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication(exclude = {org.springframework.boot.autoconfigure.liquibase.LiquibaseAutoConfiguration.class})
@Import(DmcsAcquirerPersistenceConfiguration.class)
public class SgDmcsAcquirerApplication {
    public static void main(String[] args) {
        SpringApplication.run(SgDmcsAcquirerApplication.class, args);
    }
}
