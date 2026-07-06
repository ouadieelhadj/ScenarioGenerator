package com.staging.sg.common.repository;

import com.staging.sg.common.entity.SwamAcqTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SwamAcqTransactionRepository extends JpaRepository<SwamAcqTransaction, Long> {
}
