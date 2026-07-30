package com.staging.sg.waypos.server.repository;

import com.staging.sg.waypos.server.domain.PosOutbox;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface PosOutboxRepository extends JpaRepository<PosOutbox, Long> {
    boolean existsByTransactionIdAndMessageTypeAndDestination(
            String transactionId, String messageType, String destination);

    List<PosOutbox> findTop20ByStatusAndNextAttemptAtLessThanEqualOrderById(
            String status, Instant due);
}
