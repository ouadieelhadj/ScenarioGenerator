package com.staging.sg.acquiring.repository;

import com.staging.sg.acquiring.domain.AcquiringOutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AcquiringOutboxEventRepository extends JpaRepository<AcquiringOutboxEvent, UUID> {}
