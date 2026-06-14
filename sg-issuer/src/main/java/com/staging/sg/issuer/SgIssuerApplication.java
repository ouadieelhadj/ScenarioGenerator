package com.staging.sg.issuer;

import com.staging.sg.common.JwtFilter;
import com.staging.sg.common.JwtService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.event.EventListener;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@ComponentScan(
    basePackages = {"com.staging.sg.issuer", "com.staging.sg.common"},
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE,
        classes = {JwtFilter.class, JwtService.class}
    )
)
@EntityScan(basePackages = {
    "com.staging.sg.common.entity"
})
@EnableJpaRepositories(basePackages = {
    "com.staging.sg.common.repository"
})
public class SgIssuerApplication {

    private static final Logger log = LoggerFactory.getLogger(SgIssuerApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(SgIssuerApplication.class, args);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        log.info("================================================");
        log.info("  SG Issuer — started successfully");
        log.info("  Listening on port 8200");
        log.info("================================================");
    }
}
