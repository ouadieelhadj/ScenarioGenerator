package com.staging.sg.softpos.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@EnableJpaRepositories(basePackageClasses = com.staging.sg.softpos.repository.SoftPosRepositories.class,
        considerNestedRepositories = true)
public class RepositoryConfig {
}
