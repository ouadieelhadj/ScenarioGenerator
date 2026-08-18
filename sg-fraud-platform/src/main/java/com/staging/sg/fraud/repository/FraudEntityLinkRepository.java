package com.staging.sg.fraud.repository;
import com.staging.sg.fraud.domain.FraudEntityLink;import org.springframework.data.jpa.repository.JpaRepository;import org.springframework.data.jpa.repository.Query;import org.springframework.data.repository.query.Param;import java.time.Instant;import java.util.*;
public interface FraudEntityLinkRepository extends JpaRepository<FraudEntityLink,UUID>{
 Optional<FraudEntityLink> findByMemberIdAndSectorIdAndSubjectTypeAndSubjectHashAndEntityTypeAndEntityHash(String memberId,String sectorId,String subjectType,String subjectHash,String entityType,String entityHash);
 @Query("select count(distinct l.subjectHash) from FraudEntityLink l where l.memberId=:memberId and l.entityType=:entityType and l.entityHash=:entityHash and l.lastSeenAt>=:cutoff and l.observationCount>=:minimumObservations")
 long countDistinctSubjectsAcrossSectors(@Param("memberId")String memberId,@Param("entityType")String entityType,@Param("entityHash")String entityHash,@Param("cutoff")Instant cutoff,@Param("minimumObservations")long minimumObservations);
 @Query("select count(distinct l.subjectHash) from FraudEntityLink l where l.memberId=:memberId and l.sectorId=:sectorId and l.entityType=:entityType and l.entityHash=:entityHash and l.lastSeenAt>=:cutoff and l.observationCount>=:minimumObservations")
 long countDistinctSubjectsInSector(@Param("memberId")String memberId,@Param("sectorId")String sectorId,@Param("entityType")String entityType,@Param("entityHash")String entityHash,@Param("cutoff")Instant cutoff,@Param("minimumObservations")long minimumObservations);
 List<FraudEntityLink> findTop100ByMemberIdOrderByObservationCountDesc(String memberId);
 List<FraudEntityLink> findTop50ByMemberIdAndSubjectHashOrderByObservationCountDesc(String memberId,String subjectHash);
}
