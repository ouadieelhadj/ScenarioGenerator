package com.staging.sg.common.repository;

import com.staging.sg.common.entity.SwamIssKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface SwamIssKeyRepository extends JpaRepository<SwamIssKey, Long> {
    Optional<SwamIssKey> findByMemberGroupIdAndKeyTypeAndStatus(String memberGroupId, String keyType, String status);
}
