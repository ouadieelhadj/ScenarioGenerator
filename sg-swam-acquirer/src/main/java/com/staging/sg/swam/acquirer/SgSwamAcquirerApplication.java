package com.staging.sg.swam.acquirer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {"com.staging.sg.swam.acquirer", "com.staging.sg.common"})
public class SgSwamAcquirerApplication {
    public static void main(String[] args) {
        SpringApplication.run(SgSwamAcquirerApplication.class, args);
    }
}
