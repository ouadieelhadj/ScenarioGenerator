package com.staging.sg.swam.lis.switching;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = "com.staging.sg")
@EntityScan(basePackages = {
        "com.staging.sg.common.entity",
        "com.staging.sg.swam.lis.switching.persistence"
})
@EnableJpaRepositories(basePackages = {
        "com.staging.sg.common.repository",
        "com.staging.sg.swam.lis.switching.repository"
})
public class SwamLisSwitchApplication {
    public static void main(String[] args) {
        SpringApplication.run(SwamLisSwitchApplication.class, args);
    }
}
