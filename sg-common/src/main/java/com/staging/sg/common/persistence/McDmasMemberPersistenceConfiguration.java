package com.staging.sg.common.persistence;

import com.staging.sg.common.entity.KeyStore;
import com.staging.sg.common.entity.McDmasInterface;
import com.staging.sg.common.entity.McDmasKek;
import com.staging.sg.common.entity.McDmasMemberKey;
import com.staging.sg.common.entity.User;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "sg.persistence.module", havingValue = "MC_DMAS_MEMBER")
@EnableJpaRepositories(
        basePackages = "com.staging.sg.common.repository",
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.REGEX,
                pattern = "com\\.staging\\.sg\\.common\\.repository\\."
                        + "(?!(KeyStoreRepository|McDmasInterfaceRepository|McDmasKekRepository|"
                        + "McDmasMemberKeyRepository|UserRepository)$).*"))
public class McDmasMemberPersistenceConfiguration {
    @Bean
    PersistenceManagedTypes mcDmasMemberManagedTypes() {
        return PersistenceManagedTypes.of(
                KeyStore.class.getName(), McDmasInterface.class.getName(),
                McDmasKek.class.getName(), McDmasMemberKey.class.getName(),
                User.class.getName());
    }
}
