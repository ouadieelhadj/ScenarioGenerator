package com.staging.sg.visa.base2.network;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.liquibase.LiquibaseAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;

@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class,
        HibernateJpaAutoConfiguration.class, LiquibaseAutoConfiguration.class})
public class VisaBase2NetworkSimulatorApplication {
    public static void main(String[] args) { SpringApplication.run(VisaBase2NetworkSimulatorApplication.class, args); }
}
