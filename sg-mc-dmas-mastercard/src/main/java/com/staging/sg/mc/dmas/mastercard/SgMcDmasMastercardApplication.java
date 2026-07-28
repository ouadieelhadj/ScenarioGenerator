package com.staging.sg.mc.dmas.mastercard;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@ComponentScan(basePackages = {"com.staging.sg.mc.dmas.mastercard", "com.staging.sg.common"})
public class SgMcDmasMastercardApplication {
    public static void main(String[] args) {
        SpringApplication.run(SgMcDmasMastercardApplication.class, args);
    }
}
