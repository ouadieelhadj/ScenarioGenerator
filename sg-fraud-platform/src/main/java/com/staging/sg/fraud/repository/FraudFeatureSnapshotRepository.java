package com.staging.sg.fraud.repository;
import com.staging.sg.fraud.domain.FraudFeatureSnapshot;import org.springframework.data.jpa.repository.JpaRepository;import java.util.*;
public interface FraudFeatureSnapshotRepository extends JpaRepository<FraudFeatureSnapshot,UUID>{long countByMemberId(String memberId);}
