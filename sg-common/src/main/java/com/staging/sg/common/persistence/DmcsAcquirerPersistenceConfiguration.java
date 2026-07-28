package com.staging.sg.common.persistence;

import com.staging.sg.common.entity.AcqAdvice;
import com.staging.sg.common.entity.AcqAuthorization;
import com.staging.sg.common.entity.AcqIpmFile;
import com.staging.sg.common.entity.AcqIpmRecord;
import com.staging.sg.common.entity.AcqReversal;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "sg.persistence.module", havingValue = "DMCS_ACQUIRER")
@EnableJpaRepositories(
        basePackages = "com.staging.sg.common.repository",
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.REGEX,
                pattern = "com\\.staging\\.sg\\.common\\.repository\\."
                        + "(?!(AcqAdviceRepository|AcqAuthorizationRepository|AcqIpmFileRepository|"
                        + "AcqIpmRecordRepository|AcqReversalRepository)$).*"))
public class DmcsAcquirerPersistenceConfiguration {
    @Bean
    PersistenceManagedTypes dmcsAcquirerManagedTypes() {
        return PersistenceManagedTypes.of(
                AcqAdvice.class.getName(), AcqAuthorization.class.getName(),
                AcqIpmFile.class.getName(), AcqIpmRecord.class.getName(),
                AcqReversal.class.getName());
    }
}
