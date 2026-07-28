package com.staging.sg.common.persistence;

import com.staging.sg.common.entity.KeyStore;
import com.staging.sg.common.entity.McDmasCard;
import com.staging.sg.common.entity.McDmasInterface;
import com.staging.sg.common.entity.McDmasKek;
import com.staging.sg.common.entity.McDmasMastercardKey;
import com.staging.sg.common.entity.McDmasTransaction;
import com.staging.sg.common.entity.User;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "sg.persistence.module", havingValue = "MC_DMAS_MASTERCARD")
@EnableJpaRepositories(
        basePackages = "com.staging.sg.common.repository",
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.REGEX,
                pattern = "com\\.staging\\.sg\\.common\\.repository\\."
                        + "(?!(KeyStoreRepository|McDmasCardRepository|McDmasInterfaceRepository|"
                        + "McDmasKekRepository|McDmasMastercardKeyRepository|"
                        + "McDmasTransactionRepository|UserRepository)$).*"))
public class McDmasMastercardPersistenceConfiguration {
    @Bean
    PersistenceManagedTypes mcDmasMastercardManagedTypes() {
        return PersistenceManagedTypes.of(
                KeyStore.class.getName(), McDmasCard.class.getName(),
                McDmasInterface.class.getName(), McDmasKek.class.getName(),
                McDmasMastercardKey.class.getName(), McDmasTransaction.class.getName(),
                User.class.getName());
    }
}
