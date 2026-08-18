package com.staging.sg.fraud.repository;

import com.staging.sg.fraud.domain.FraudEventRoute;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface FraudEventRouteRepository extends JpaRepository<FraudEventRoute, UUID> {
    Optional<FraudEventRoute> findByMemberIdAndSectorIdAndEventType(String memberId, String sectorId, String eventType);
    List<FraudEventRoute> findByMemberIdOrderBySectorIdAscEventTypeAsc(String memberId);
}
