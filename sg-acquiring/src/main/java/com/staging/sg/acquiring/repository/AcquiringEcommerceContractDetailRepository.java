package com.staging.sg.acquiring.repository;

import com.staging.sg.acquiring.domain.AcquiringEcommerceContractDetail;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AcquiringEcommerceContractDetailRepository
        extends JpaRepository<AcquiringEcommerceContractDetail, UUID> {}
