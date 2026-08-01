package com.staging.sg.acquiring.repository;

import com.staging.sg.acquiring.domain.AcceptanceProduct;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AcceptanceProductRepository extends JpaRepository<AcceptanceProduct, UUID> {}
