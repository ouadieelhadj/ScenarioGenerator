package com.staging.sg.acquiring.repository;

import com.staging.sg.acquiring.domain.MerchantOutletProduct;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface MerchantOutletProductRepository extends JpaRepository<MerchantOutletProduct, UUID> {
    Optional<MerchantOutletProduct> findByOutletIdAndProductIdAndActiveTrue(UUID outletId, UUID productId);
}
