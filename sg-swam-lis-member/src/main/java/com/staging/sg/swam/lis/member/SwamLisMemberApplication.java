package com.staging.sg.swam.lis.member;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {
        "com.staging.sg.swam.lis.member",
        "com.staging.sg.swam.lis.common",
        "com.staging.sg.common.persistence"
})
public class SwamLisMemberApplication {
    public static void main(String[] args) {
        SpringApplication.run(SwamLisMemberApplication.class, args);
    }
}
