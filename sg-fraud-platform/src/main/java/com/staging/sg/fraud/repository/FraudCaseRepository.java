package com.staging.sg.fraud.repository;
import com.staging.sg.fraud.domain.FraudCase; import org.springframework.data.jpa.repository.JpaRepository; import java.util.*;
public interface FraudCaseRepository extends JpaRepository<FraudCase,UUID>{Optional<FraudCase> findByMemberIdAndAlertId(String memberId,UUID alertId);List<FraudCase> findTop100ByMemberIdOrderByCreatedAtDesc(String memberId);}
