package com.staging.sg.fraud.repository;
import com.staging.sg.fraud.domain.FraudMonitoringSubject;import org.springframework.data.jpa.repository.JpaRepository;import java.util.*;
public interface FraudMonitoringSubjectRepository extends JpaRepository<FraudMonitoringSubject,UUID>{Optional<FraudMonitoringSubject> findByMemberIdAndSectorIdAndSubjectTypeAndSubjectHash(String memberId,String sectorId,String subjectType,String subjectHash);}
