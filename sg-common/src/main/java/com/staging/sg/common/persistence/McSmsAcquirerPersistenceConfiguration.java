package com.staging.sg.common.persistence;

import com.staging.sg.common.entity.NetworkRef;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "sg.persistence.module", havingValue = "MC_SMS_ACQUIRER")
@EnableJpaRepositories(
        basePackages = {
                "com.staging.sg.common.repository",
                "com.staging.sg.mc.sms.acquirer.repository"
        },
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.REGEX,
                pattern = "com\\.staging\\.sg\\.common\\.repository\\.(?!NetworkRepository$).*"))
public class McSmsAcquirerPersistenceConfiguration {
    @Bean
    PersistenceManagedTypes mcSmsAcquirerManagedTypes() {
        return PersistenceManagedTypes.of(
                NetworkRef.class.getName(),
                "com.staging.sg.mc.sms.acquirer.entity.McSmsAcqKey",
                "com.staging.sg.mc.sms.acquirer.entity.McSmsAcqTransaction",
                "com.staging.sg.mc.sms.acquirer.entity.McSmsKek");
    }
}
