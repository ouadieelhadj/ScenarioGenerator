package com.staging.sg.onboarding;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class MerchantOnboardingApplication {
    public static void main(String[] args) {
        SpringApplication.run(MerchantOnboardingApplication.class, args);
    }
}
