package com.staging.sg.common.repository;

import com.staging.sg.common.entity.McDmasMemberTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface McDmasMemberTransactionRepository
        extends JpaRepository<McDmasMemberTransaction, Long> {

    Optional<McDmasMemberTransaction> findByBankCodeAndStanAndTransmissionDatetime(
            String bankCode, String stan, String transmissionDatetime);

    Optional<McDmasMemberTransaction> findByPanAndStanAndTransmissionDatetime(
            String pan, String stan, String transmissionDatetime);

    List<McDmasMemberTransaction> findByClearingEligibleTrueAndClearingExtractedAtIsNull();

    List<McDmasMemberTransaction> findByClearingEligibleTrueOrderById();
}
