package com.staging.sg.dmcs.acquirer.service;

import com.staging.sg.common.entity.DmcsAcquirerClearingTransaction;
import com.staging.sg.common.repository.DmcsAcquirerClearingTransactionRepository;
import com.staging.sg.common.repository.McDmasMemberTransactionRepository;
import com.staging.sg.common.service.DmcAuthorizationToClearingMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
public class DmcAcquirerEodService {
    private final McDmasMemberTransactionRepository authorizationRepository;
    private final DmcsAcquirerClearingTransactionRepository clearingRepository;

    @Value("${mc.acquirer.defaults.DE093_DEST_ID:00000000000}")
    private String destinationId;
    @Value("${mc.acquirer.defaults.DE094_ORIGIN_ID:00000000000}")
    private String originId;

    public DmcAcquirerEodService(
            McDmasMemberTransactionRepository authorizationRepository,
            DmcsAcquirerClearingTransactionRepository clearingRepository) {
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
                    new DmcsAcquirerClearingTransaction(), authorization, businessDate,
                    destinationId, originId);
            clearingRepository.save(clearing);
            created++;
        }
        return new EodResult(businessDate, eligible, created, eligible - created);
    }

    public record EodResult(LocalDate businessDate, int eligible, int created, int alreadyPresent) {
    }
}
