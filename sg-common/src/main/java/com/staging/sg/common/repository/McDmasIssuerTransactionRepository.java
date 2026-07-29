package com.staging.sg.common.repository;

import com.staging.sg.common.entity.McDmasIssuerTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface McDmasIssuerTransactionRepository
        extends JpaRepository<McDmasIssuerTransaction, Long> {

    Optional<McDmasIssuerTransaction> findByBankCodeAndStanAndTransmissionDatetime(
            String bankCode, String stan, String transmissionDatetime);

    Optional<McDmasIssuerTransaction> findByPanAndStanAndTransmissionDatetime(
            String pan, String stan, String transmissionDatetime);

    List<McDmasIssuerTransaction> findByClearingEligibleTrueAndClearingExtractedAtIsNull();

    List<McDmasIssuerTransaction> findByClearingEligibleTrueOrderById();
}
