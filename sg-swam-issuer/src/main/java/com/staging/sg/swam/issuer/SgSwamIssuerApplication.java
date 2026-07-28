package com.staging.sg.swam.issuer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {"com.staging.sg.swam.issuer", "com.staging.sg.common"})
public class SgSwamIssuerApplication {
    public static void main(String[] args) {
        SpringApplication.run(SgSwamIssuerApplication.class, args);
    }
}
