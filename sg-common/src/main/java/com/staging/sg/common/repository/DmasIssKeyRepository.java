package com.staging.sg.common.repository;

import com.staging.sg.common.entity.DmasIssKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DmasIssKeyRepository extends JpaRepository<DmasIssKey, Long> {
    Optional<DmasIssKey> findByMemberGroupIdAndKeyTypeAndStatus(String memberGroupId, String keyType, String status);
}
