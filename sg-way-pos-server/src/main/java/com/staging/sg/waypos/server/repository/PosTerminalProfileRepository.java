package com.staging.sg.waypos.server.repository;

import com.staging.sg.waypos.server.domain.PosTerminalProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface PosTerminalProfileRepository
        extends JpaRepository<PosTerminalProfile, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PosTerminalProfile> findLockedByTerminalId(String terminalId);
}
