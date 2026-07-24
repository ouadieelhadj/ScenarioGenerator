package com.staging.sg.common.repository;

import com.staging.sg.common.entity.McDmasTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface McDmasTransactionRepository extends JpaRepository<McDmasTransaction, Long> {
    Optional<McDmasTransaction> findByStanAndTransmissionDt(String stan, String transmissionDt);
}
