package com.staging.sg.acquiring.repository;

import com.staging.sg.acquiring.domain.EcommerceStore;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;
import java.util.Optional;

public interface EcommerceStoreRepository extends JpaRepository<EcommerceStore, UUID> {
    Optional<EcommerceStore> findByMerchantIdAndStoreCode(UUID merchantId, String storeCode);
}
