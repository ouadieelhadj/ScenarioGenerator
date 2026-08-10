package com.staging.sg.acquiring.repository;

import com.staging.sg.acquiring.domain.MerchantRepresentative;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface MerchantRepresentativeRepository extends JpaRepository<MerchantRepresentative, UUID> {
    List<MerchantRepresentative> findByMerchantIdAndActiveTrue(UUID merchantId);
}
