package com.staging.sg.common.repository;

import com.staging.sg.common.entity.DmasTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DmasTransactionRepository extends JpaRepository<DmasTransaction, Long> {
    Optional<DmasTransaction> findByStanAndTransmissionDt(String stan, String transmissionDt);
}
