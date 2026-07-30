package com.staging.sg.waypos.server.repository;

import com.staging.sg.waypos.server.domain.PosHold;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface PosHoldRepository extends JpaRepository<PosHold, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PosHold> findLockedByTransactionId(String transactionId);
}
