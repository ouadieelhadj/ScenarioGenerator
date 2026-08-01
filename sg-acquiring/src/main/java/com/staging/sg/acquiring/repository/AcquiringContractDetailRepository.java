package com.staging.sg.acquiring.repository;

import com.staging.sg.acquiring.domain.AcquiringContractDetail;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AcquiringContractDetailRepository extends JpaRepository<AcquiringContractDetail, UUID> {}
