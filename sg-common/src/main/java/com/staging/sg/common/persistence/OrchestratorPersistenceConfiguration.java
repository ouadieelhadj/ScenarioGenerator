package com.staging.sg.common.persistence;

import com.staging.sg.common.entity.AcqAdvice;
import com.staging.sg.common.entity.AcqAuthorization;
import com.staging.sg.common.entity.AcqReversal;
import com.staging.sg.common.entity.BinRange;
import com.staging.sg.common.entity.Campaign;
import com.staging.sg.common.entity.CampaignExecution;
import com.staging.sg.common.entity.CampaignExecutionResult;
import com.staging.sg.common.entity.CampaignLoadStep;
import com.staging.sg.common.entity.Execution;
import com.staging.sg.common.entity.IsoFieldCatalog;
import com.staging.sg.common.entity.MessageType;
import com.staging.sg.common.entity.NetworkRef;
import com.staging.sg.common.entity.Permission;
import com.staging.sg.common.entity.Result;
import com.staging.sg.common.entity.Role;
import com.staging.sg.common.entity.SgOrchestratorCardDmas;
import com.staging.sg.common.entity.Test;
import com.staging.sg.common.entity.TpsStep;
import com.staging.sg.common.entity.User;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "sg.persistence.module", havingValue = "ORCHESTRATOR")
@EnableJpaRepositories(
        basePackages = "com.staging.sg.common.repository",
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.REGEX,
                pattern = "com\\.staging\\.sg\\.common\\.repository\\."
                        + "(?!(AcqAdviceRepository|AcqAuthorizationRepository|AcqReversalRepository|"
                        + "BinRangeRepository|CampaignExecutionRepository|CampaignExecutionResultRepository|"
                        + "CampaignLoadStepRepository|"
                        + "CampaignRepository|ExecutionRepository|IsoFieldCatalogRepository|"
                        + "MessageTypeRepository|NetworkRepository|PermissionRepository|ResultRepository|RoleRepository|"
                        + "SgOrchestratorCardDmasRepository|TestRepository|TpsStepRepository|"
                        + "UserRepository)$).*"))
public class OrchestratorPersistenceConfiguration {
    @Bean
    PersistenceManagedTypes orchestratorManagedTypes() {
        return PersistenceManagedTypes.of(
                AcqAdvice.class.getName(), AcqAuthorization.class.getName(),
                AcqReversal.class.getName(), BinRange.class.getName(),
                Campaign.class.getName(), CampaignExecution.class.getName(),
                CampaignExecutionResult.class.getName(),
                CampaignLoadStep.class.getName(), Execution.class.getName(),
                IsoFieldCatalog.class.getName(), MessageType.class.getName(),
                NetworkRef.class.getName(), Permission.class.getName(), Result.class.getName(),
                Role.class.getName(), SgOrchestratorCardDmas.class.getName(),
                Test.class.getName(), TpsStep.class.getName(),
                User.class.getName());
    }
}
