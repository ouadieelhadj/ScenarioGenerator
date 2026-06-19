package com.staging.sg.dmas.acquirer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@EntityScan(basePackages = "com.staging.sg.common.entity")
@EnableJpaRepositories(basePackages = "com.staging.sg.common.repository")
@ComponentScan(basePackages = {"com.staging.sg.dmas.acquirer", "com.staging.sg.common"})
public class SgDmasAcquirerApplication {
    public static void main(String[] args) {
        SpringApplication.run(SgDmasAcquirerApplication.class, args);
    }
}
