package com.staging.sg.swam.lis.switching;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {
        "com.staging.sg.swam.lis.switching",
        "com.staging.sg.swam.lis.common",
        "com.staging.sg.common.persistence"
})
public class SwamLisSwitchApplication {
    public static void main(String[] args) {
        SpringApplication.run(SwamLisSwitchApplication.class, args);
    }
}
