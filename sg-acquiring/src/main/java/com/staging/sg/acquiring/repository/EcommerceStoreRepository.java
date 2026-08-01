package com.staging.sg.acquiring.repository;

import com.staging.sg.acquiring.domain.EcommerceStore;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface EcommerceStoreRepository extends JpaRepository<EcommerceStore, UUID> {}
