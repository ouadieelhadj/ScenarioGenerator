package com.staging.sg.common.repository;

import com.staging.sg.common.entity.McDmasMastercardKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface McDmasMastercardKeyRepository extends JpaRepository<McDmasMastercardKey, Long> {
    Optional<McDmasMastercardKey> findByMemberGroupIdAndKeyTypeAndStatus(String memberGroupId, String keyType, String status);
}
