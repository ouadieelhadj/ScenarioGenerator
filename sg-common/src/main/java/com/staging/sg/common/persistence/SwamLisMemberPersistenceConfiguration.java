package com.staging.sg.common.persistence;

import com.staging.sg.common.entity.SwamAcqTransaction;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "sg.persistence.module", havingValue = "SWAM_LIS_MEMBER")
@EnableJpaRepositories(
        basePackages = {
                "com.staging.sg.common.repository",
                "com.staging.sg.swam.lis.member.repository"
        },
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.REGEX,
                pattern = "com\\.staging\\.sg\\.common\\.repository\\."
                        + "(?!SwamAcqTransactionRepository$).*"))
public class SwamLisMemberPersistenceConfiguration {
    @Bean
    PersistenceManagedTypes swamLisMemberManagedTypes() {
        return PersistenceManagedTypes.of(
                SwamAcqTransaction.class.getName(),
                "com.staging.sg.swam.lis.member.persistence.MemberAccountingEntry",
                "com.staging.sg.swam.lis.member.persistence.MemberBatchExecution",
                "com.staging.sg.swam.lis.member.persistence.MemberBusinessDay",
                "com.staging.sg.swam.lis.member.persistence.MemberChargeback",
                "com.staging.sg.swam.lis.member.persistence.MemberClearingTransaction",
                "com.staging.sg.swam.lis.member.persistence.MemberLisFile");
    }
}
