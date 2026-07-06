package com.staging.sg.common.repository;

import com.staging.sg.common.entity.SwamIssTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SwamIssTransactionRepository extends JpaRepository<SwamIssTransaction, Long> {
}
