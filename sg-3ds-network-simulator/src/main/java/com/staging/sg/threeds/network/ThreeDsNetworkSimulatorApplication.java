package com.staging.sg.threeds.network;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class,
        HibernateJpaAutoConfiguration.class,
        UserDetailsServiceAutoConfiguration.class})
public class ThreeDsNetworkSimulatorApplication {
    public static void main(String[] args) {
        SpringApplication.run(ThreeDsNetworkSimulatorApplication.class, args);
    }
}
