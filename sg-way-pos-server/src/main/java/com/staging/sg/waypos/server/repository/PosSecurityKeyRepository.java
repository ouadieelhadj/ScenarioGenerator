package com.staging.sg.waypos.server.repository;

import com.staging.sg.waypos.server.domain.PosSecurityKey;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PosSecurityKeyRepository extends JpaRepository<PosSecurityKey, String> {
}
