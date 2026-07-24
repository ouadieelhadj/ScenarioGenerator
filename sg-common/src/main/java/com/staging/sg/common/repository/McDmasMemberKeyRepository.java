package com.staging.sg.common.repository;

import com.staging.sg.common.entity.McDmasMemberKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface McDmasMemberKeyRepository extends JpaRepository<McDmasMemberKey, Long> {
    Optional<McDmasMemberKey> findByMemberGroupIdAndKeyTypeAndStatus(String memberGroupId, String keyType, String status);
}
