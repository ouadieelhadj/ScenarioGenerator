package com.staging.sg.common.repository;

import com.staging.sg.common.entity.DmasAcqKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DmasAcqKeyRepository extends JpaRepository<DmasAcqKey, Long> {
    Optional<DmasAcqKey> findByMemberGroupIdAndKeyTypeAndStatus(String memberGroupId, String keyType, String status);
}
