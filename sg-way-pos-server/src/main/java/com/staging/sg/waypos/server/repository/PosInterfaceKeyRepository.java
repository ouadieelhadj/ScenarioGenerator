package com.staging.sg.waypos.server.repository;

import com.staging.sg.waypos.server.domain.PosInterfaceKey;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PosInterfaceKeyRepository extends JpaRepository<PosInterfaceKey, String> {
}
