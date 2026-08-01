package com.staging.sg.acquiring.repository;

import com.staging.sg.acquiring.domain.TerminalDevice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TerminalDeviceRepository extends JpaRepository<TerminalDevice, UUID> {}
