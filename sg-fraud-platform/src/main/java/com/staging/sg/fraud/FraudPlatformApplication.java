package com.staging.sg.fraud;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication @EnableScheduling
public class FraudPlatformApplication {
    public static void main(String[] args) { SpringApplication.run(FraudPlatformApplication.class, args); }
}
