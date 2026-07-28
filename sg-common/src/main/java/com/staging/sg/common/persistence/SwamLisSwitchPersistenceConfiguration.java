package com.staging.sg.common.persistence;

import com.staging.sg.common.entity.SwamIssTransaction;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "sg.persistence.module", havingValue = "SWAM_LIS_SWITCH")
@EnableJpaRepositories(
        basePackages = {
                "com.staging.sg.common.repository",
                "com.staging.sg.swam.lis.switching.repository"
        },
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.REGEX,
                pattern = "com\\.staging\\.sg\\.common\\.repository\\."
                        + "(?!SwamIssTransactionRepository$).*"))
public class SwamLisSwitchPersistenceConfiguration {
    @Bean
    PersistenceManagedTypes swamLisSwitchManagedTypes() {
        return PersistenceManagedTypes.of(
                SwamIssTransaction.class.getName(),
                "com.staging.sg.swam.lis.switching.persistence.SwitchAccountingEntry",
                "com.staging.sg.swam.lis.switching.persistence.SwitchBatchExecution",
                "com.staging.sg.swam.lis.switching.persistence.SwitchBusinessDay",
                "com.staging.sg.swam.lis.switching.persistence.SwitchChargeback",
                "com.staging.sg.swam.lis.switching.persistence.SwitchClearingTransaction",
                "com.staging.sg.swam.lis.switching.persistence.SwitchLisFile");
    }
}
