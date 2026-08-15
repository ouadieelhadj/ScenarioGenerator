package com.staging.sg.fraud.repository;
import com.staging.sg.fraud.domain.FraudAlert; import org.springframework.data.jpa.repository.JpaRepository; import java.util.*;
public interface FraudAlertRepository extends JpaRepository<FraudAlert,UUID>{Optional<FraudAlert> findByIdAndMemberId(UUID id,String memberId);List<FraudAlert> findTop100ByMemberIdOrderByCreatedAtDesc(String memberId);Optional<FraudAlert> findByMemberIdAndTransactionReference(String memberId,String tx);}
