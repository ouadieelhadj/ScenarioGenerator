package com.staging.sg.visa.visanet.simulator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.liquibase.LiquibaseAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;

@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class,
        HibernateJpaAutoConfiguration.class, LiquibaseAutoConfiguration.class})
public class VisaNetSimulatorApplication {
    public static void main(String[] args) { SpringApplication.run(VisaNetSimulatorApplication.class, args); }
}
