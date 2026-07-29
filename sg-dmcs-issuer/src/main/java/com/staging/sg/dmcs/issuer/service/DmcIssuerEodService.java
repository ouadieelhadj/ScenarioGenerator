package com.staging.sg.dmcs.issuer.service;

import com.staging.sg.common.entity.DmcsIssuerClearingTransaction;
import com.staging.sg.common.repository.DmcsIssuerClearingTransactionRepository;
import com.staging.sg.common.repository.McDmasIssuerTransactionRepository;
import com.staging.sg.common.service.DmcAuthorizationToClearingMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
public class DmcIssuerEodService {
    private final McDmasIssuerTransactionRepository authorizationRepository;
    private final DmcsIssuerClearingTransactionRepository clearingRepository;

    @Value("${mc.issuer.defaults.DE093_DEST_ID:00000000000}")
    private String destinationId;
    @Value("${mc.issuer.defaults.DE094_ORIGIN_ID:00000000000}")
    private String originId;

    public DmcIssuerEodService(
            McDmasIssuerTransactionRepository authorizationRepository,
            DmcsIssuerClearingTransactionRepository clearingRepository) {
        this.authorizationRepository = authorizationRepository;
        this.clearingRepository = clearingRepository;
    }

    @Transactional
    public EodResult run(LocalDate businessDate) {
        int eligible = 0;
        int created = 0;
        for (var authorization : authorizationRepository.findByClearingEligibleTrueOrderById()) {
            eligible++;
            if (clearingRepository.existsBySourceTypeAndLocalAuthorizationIdAndLifecycleStage(
                    "LOCAL_AUTH", authorization.getId(), "FIRST_PRESENTMENT")) {
                continue;
            }
            var clearing = DmcAuthorizationToClearingMapper.populateFirstPresentment(
                    new DmcsIssuerClearingTransaction(), authorization, businessDate,
                    destinationId, originId);
            clearingRepository.save(clearing);
            created++;
        }
        return new EodResult(businessDate, eligible, created, eligible - created);
    }

    public record EodResult(LocalDate businessDate, int eligible, int created, int alreadyPresent) {
    }
}
