package com.staging.sg.acquiring.repository;

import com.staging.sg.acquiring.domain.MerchantOutlet;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface MerchantOutletRepository extends JpaRepository<MerchantOutlet, UUID> {}
