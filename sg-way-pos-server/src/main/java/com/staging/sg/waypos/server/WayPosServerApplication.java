package com.staging.sg.waypos.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class WayPosServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(WayPosServerApplication.class, args);
    }
}
