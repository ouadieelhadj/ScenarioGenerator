package com.staging.sg.acquiring.repository;

import com.staging.sg.acquiring.domain.MerchantOutlet;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;
import java.util.List;
import java.util.Optional;

public interface MerchantOutletRepository extends JpaRepository<MerchantOutlet, UUID> {
    Optional<MerchantOutlet> findFirstByMerchantIdAndPrincipalTrueAndActiveTrue(UUID merchantId);
    Optional<MerchantOutlet> findByMerchantIdAndOutletCode(UUID merchantId, String outletCode);
    List<MerchantOutlet> findByMerchantIdAndActiveTrueOrderByCreatedAtAsc(UUID merchantId);
}
