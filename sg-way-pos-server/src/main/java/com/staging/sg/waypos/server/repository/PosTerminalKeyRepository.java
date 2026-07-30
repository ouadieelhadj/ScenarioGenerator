package com.staging.sg.waypos.server.repository;

import com.staging.sg.waypos.server.domain.PosTerminalKey;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PosTerminalKeyRepository extends JpaRepository<PosTerminalKey, Long> {
    List<PosTerminalKey> findTop15ByTerminalIdAndKeyStatusInOrderByIdAsc(
            String terminalId, Collection<String> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PosTerminalKey> findByTerminalIdAndKeyTypeAndKeyId(
            String terminalId, String keyType, String keyId);

    List<PosTerminalKey> findByTerminalIdAndKeyTypeAndKeyStatusIn(
            String terminalId, String keyType, Collection<String> statuses);
}
