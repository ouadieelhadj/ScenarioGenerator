package com.staging.sg.acquiring.repository;

import com.staging.sg.acquiring.domain.MerchantLegalProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface MerchantLegalProfileRepository extends JpaRepository<MerchantLegalProfile, UUID> {}
