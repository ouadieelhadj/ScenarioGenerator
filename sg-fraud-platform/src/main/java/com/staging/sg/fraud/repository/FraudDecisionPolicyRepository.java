package com.staging.sg.fraud.repository;
import com.staging.sg.fraud.domain.FraudDecisionPolicy;import org.springframework.data.jpa.repository.JpaRepository;import java.util.*;
public interface FraudDecisionPolicyRepository extends JpaRepository<FraudDecisionPolicy,UUID>{Optional<FraudDecisionPolicy> findByMemberId(String memberId);}
