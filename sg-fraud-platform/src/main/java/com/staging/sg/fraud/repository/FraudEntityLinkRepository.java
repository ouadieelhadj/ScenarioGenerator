package com.staging.sg.fraud.repository;
import com.staging.sg.fraud.domain.FraudEntityLink;import org.springframework.data.jpa.repository.JpaRepository;import java.util.*;
public interface FraudEntityLinkRepository extends JpaRepository<FraudEntityLink,UUID>{
 Optional<FraudEntityLink> findByMemberIdAndSubjectHashAndEntityTypeAndEntityHash(String memberId,String subjectHash,String entityType,String entityHash);
 long countDistinctByMemberIdAndEntityTypeAndEntityHash(String memberId,String entityType,String entityHash);
}
