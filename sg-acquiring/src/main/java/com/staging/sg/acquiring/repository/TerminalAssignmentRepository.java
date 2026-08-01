package com.staging.sg.acquiring.repository;

import com.staging.sg.acquiring.domain.TerminalAssignment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TerminalAssignmentRepository extends JpaRepository<TerminalAssignment, UUID> {
    Optional<TerminalAssignment> findByTerminalDeviceIdAndActiveTrue(UUID terminalDeviceId);
}
