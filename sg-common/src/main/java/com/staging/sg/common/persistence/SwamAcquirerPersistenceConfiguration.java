package com.staging.sg.common.persistence;

import com.staging.sg.common.entity.NetworkRef;
import com.staging.sg.common.entity.SwamAcqKey;
import com.staging.sg.common.entity.SwamAcqTransaction;
import com.staging.sg.common.entity.SwamAcquirerCard;
import com.staging.sg.common.entity.SwamInterface;
import com.staging.sg.common.entity.SwamKek;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "sg.persistence.module", havingValue = "SWAM_ACQUIRER")
@EnableJpaRepositories(
        basePackages = "com.staging.sg.common.repository",
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.REGEX,
                pattern = "com\\.staging\\.sg\\.common\\.repository\\."
                        + "(?!(NetworkRepository|SwamAcqKeyRepository|SwamAcqTransactionRepository|"
                        + "SwamAcquirerCardRepository|SwamInterfaceRepository|SwamKekRepository)$).*"))
public class SwamAcquirerPersistenceConfiguration {
    @Bean
    PersistenceManagedTypes swamAcquirerManagedTypes() {
        return PersistenceManagedTypes.of(
                NetworkRef.class.getName(), SwamAcqKey.class.getName(),
                SwamAcqTransaction.class.getName(), SwamAcquirerCard.class.getName(),
                SwamInterface.class.getName(), SwamKek.class.getName());
    }
}
