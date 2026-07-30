package com.staging.sg.waypos.server.repository;

import com.staging.sg.waypos.server.domain.PosBatchUpload;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PosBatchUploadRepository extends JpaRepository<PosBatchUpload, Long> {
    boolean existsByTerminalIdAndBatchIdAndMessageFingerprint(
            String terminalId, String batchId, String fingerprint);
    List<PosBatchUpload> findByTerminalIdAndBatchIdOrderById(
            String terminalId, String batchId);
}
