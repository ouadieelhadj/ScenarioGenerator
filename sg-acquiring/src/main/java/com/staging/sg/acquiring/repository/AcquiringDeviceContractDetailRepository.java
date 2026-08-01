package com.staging.sg.acquiring.repository;

import com.staging.sg.acquiring.domain.AcquiringDeviceContractDetail;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AcquiringDeviceContractDetailRepository extends JpaRepository<AcquiringDeviceContractDetail, UUID> {
    Optional<AcquiringDeviceContractDetail> findByTerminalId(String terminalId);
}
