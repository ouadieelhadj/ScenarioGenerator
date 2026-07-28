package com.staging.sg.mc.dmas.member;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {"com.staging.sg.mc.dmas.member", "com.staging.sg.common"})
public class SgMcDmasMemberApplication {
    public static void main(String[] args) {
        SpringApplication.run(SgMcDmasMemberApplication.class, args);
    }
}
