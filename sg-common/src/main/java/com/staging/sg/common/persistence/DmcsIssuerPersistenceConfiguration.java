package com.staging.sg.common.persistence;

import com.staging.sg.common.entity.IssIpmFile;
import com.staging.sg.common.entity.IssIpmRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "sg.persistence.module", havingValue = "DMCS_ISSUER")
@EnableJpaRepositories(
        basePackages = "com.staging.sg.common.repository",
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.REGEX,
                pattern = "com\\.staging\\.sg\\.common\\.repository\\."
                        + "(?!(IssIpmFileRepository|IssIpmRecordRepository)$).*"))
public class DmcsIssuerPersistenceConfiguration {
    @Bean
    PersistenceManagedTypes dmcsIssuerManagedTypes() {
        return PersistenceManagedTypes.of(
                IssIpmFile.class.getName(), IssIpmRecord.class.getName());
    }
}
