package com.staging.sg.fraud.repository;
import com.staging.sg.fraud.domain.FraudAiPolicy;import org.springframework.data.jpa.repository.JpaRepository;import java.util.*;
public interface FraudAiPolicyRepository extends JpaRepository<FraudAiPolicy,UUID>{Optional<FraudAiPolicy> findByMemberIdAndSectorId(String memberId,String sectorId);List<FraudAiPolicy> findByMemberIdOrderBySectorId(String memberId);}
