package com.staging.sg.common.repository;

import com.staging.sg.common.entity.McDmasKek;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface McDmasKekRepository extends JpaRepository<McDmasKek, Long> {
    Optional<McDmasKek> findByMemberGroupId(String memberGroupId);
    Optional<McDmasKek> findByMemberGroupIdAndStatus(String memberGroupId, String status);
}
