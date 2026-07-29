package com.staging.sg.common.persistence;

import com.staging.sg.common.entity.AcqIpmFile;
import com.staging.sg.common.entity.AcqIpmRecord;
import com.staging.sg.common.entity.DmcsAcquirerClearingTransaction;
import com.staging.sg.common.entity.McDmasMemberTransaction;
import com.staging.sg.common.entity.IpmProcessingLog;
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
                        + "(?!(AcqIpmFileRepository|AcqIpmRecordRepository|"
                        + "DmcsAcquirerClearingTransactionRepository|IpmProcessingLogRepository|"
                        + "McDmasMemberTransactionRepository)$).*"))
public class DmcsAcquirerPersistenceConfiguration {
    @Bean
    PersistenceManagedTypes dmcsAcquirerManagedTypes() {
        return PersistenceManagedTypes.of(
                AcqIpmFile.class.getName(), AcqIpmRecord.class.getName(),
                DmcsAcquirerClearingTransaction.class.getName(),
                IpmProcessingLog.class.getName(),
                McDmasMemberTransaction.class.getName());
    }
}
