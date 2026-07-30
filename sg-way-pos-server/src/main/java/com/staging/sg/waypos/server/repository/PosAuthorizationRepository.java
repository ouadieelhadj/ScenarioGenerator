package com.staging.sg.waypos.server.repository;

import com.staging.sg.waypos.server.domain.PosAuthorization;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.Collection;
import java.util.List;

public interface PosAuthorizationRepository extends JpaRepository<PosAuthorization, String> {
    Optional<PosAuthorization> findByIdempotencyKey(String idempotencyKey);
    Optional<PosAuthorization> findFirstByRrnOrderByCreatedAtDesc(String rrn);
    Optional<PosAuthorization> findFirstByRrnAndTransactionIdNotOrderByCreatedAtDesc(
            String rrn, String transactionId);
    List<PosAuthorization> findByTerminalIdAndBatchIdAndStatusIn(
            String terminalId, String batchId, Collection<String> statuses);
}
