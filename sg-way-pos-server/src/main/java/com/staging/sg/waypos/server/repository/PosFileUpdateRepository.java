package com.staging.sg.waypos.server.repository;

import com.staging.sg.waypos.server.domain.PosFileUpdate;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PosFileUpdateRepository extends JpaRepository<PosFileUpdate, Long> {
    boolean existsByTerminalIdAndMessageFingerprint(
            String terminalId, String messageFingerprint);
}
