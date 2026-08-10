package com.staging.sg.acquiring.repository;

import com.staging.sg.acquiring.domain.MerchantBeneficialOwner;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface MerchantBeneficialOwnerRepository extends JpaRepository<MerchantBeneficialOwner, UUID> {
    List<MerchantBeneficialOwner> findByMerchantIdAndActiveTrue(UUID merchantId);
}
