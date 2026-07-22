package com.staging.sg.common.repository;

import com.staging.sg.common.entity.SgOrchestratorCardDmas;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SgOrchestratorCardDmasRepository
        extends JpaRepository<SgOrchestratorCardDmas, Long> {

    Optional<SgOrchestratorCardDmas> findByPan(String pan);

    List<SgOrchestratorCardDmas> findByStatus(String status);
}
