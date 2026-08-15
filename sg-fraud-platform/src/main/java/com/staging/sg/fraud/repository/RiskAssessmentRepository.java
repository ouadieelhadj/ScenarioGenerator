package com.staging.sg.fraud.repository;
import com.staging.sg.fraud.domain.RiskAssessment; import org.springframework.data.jpa.repository.JpaRepository; import java.util.*;
public interface RiskAssessmentRepository extends JpaRepository<RiskAssessment,UUID>{Optional<RiskAssessment> findByMemberIdAndTransactionReference(String memberId,String transactionReference);}
