package com.staging.sg.onboarding.repository;

import com.staging.sg.onboarding.domain.TerminalRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TerminalRequestRepository extends JpaRepository<TerminalRequest, UUID> {
    List<TerminalRequest> findByCaseIdOrderByCreatedAtAsc(UUID caseId);
    List<TerminalRequest> findByOutletIdOrderByCreatedAtAsc(UUID outletId);
}
