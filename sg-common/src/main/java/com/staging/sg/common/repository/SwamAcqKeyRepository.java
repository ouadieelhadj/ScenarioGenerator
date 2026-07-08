package com.staging.sg.common.repository;

import com.staging.sg.common.entity.SwamAcqKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface SwamAcqKeyRepository extends JpaRepository<SwamAcqKey, Long> {
    Optional<SwamAcqKey> findByMemberGroupIdAndKeyTypeAndStatus(String memberGroupId, String keyType, String status);
}
