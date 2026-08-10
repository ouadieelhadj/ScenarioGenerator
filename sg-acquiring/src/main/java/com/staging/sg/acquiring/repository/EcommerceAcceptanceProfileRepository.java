package com.staging.sg.acquiring.repository;

import com.staging.sg.acquiring.domain.EcommerceAcceptanceProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;
import java.util.Optional;

public interface EcommerceAcceptanceProfileRepository extends JpaRepository<EcommerceAcceptanceProfile, UUID> {
    Optional<EcommerceAcceptanceProfile> findByStoreIdAndContractId(UUID storeId, UUID contractId);
}
