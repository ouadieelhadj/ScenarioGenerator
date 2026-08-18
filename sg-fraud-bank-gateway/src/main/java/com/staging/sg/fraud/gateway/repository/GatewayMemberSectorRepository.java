package com.staging.sg.fraud.gateway.repository;

import com.staging.sg.fraud.gateway.domain.GatewayMemberSector;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface GatewayMemberSectorRepository extends JpaRepository<GatewayMemberSector, UUID> {
    boolean existsByMemberIdAndSectorIdAndActiveTrue(String memberId, String sectorId);
    List<GatewayMemberSector> findByMemberIdAndActiveTrueOrderBySectorId(String memberId);
}
