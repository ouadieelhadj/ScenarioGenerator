package com.staging.sg.fraud.repository;
import com.staging.sg.fraud.domain.FraudGraphPolicy;import org.springframework.data.jpa.repository.JpaRepository;import java.util.*;
public interface FraudGraphPolicyRepository extends JpaRepository<FraudGraphPolicy,UUID>{Optional<FraudGraphPolicy> findByMemberIdAndSectorId(String memberId,String sectorId);List<FraudGraphPolicy> findByMemberIdOrderBySectorId(String memberId);}
