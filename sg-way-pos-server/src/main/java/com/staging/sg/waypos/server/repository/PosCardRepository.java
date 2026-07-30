package com.staging.sg.waypos.server.repository;

import com.staging.sg.waypos.server.domain.PosCard;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface PosCardRepository extends JpaRepository<PosCard, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PosCard> findLockedByPanHash(String panHash);
}
