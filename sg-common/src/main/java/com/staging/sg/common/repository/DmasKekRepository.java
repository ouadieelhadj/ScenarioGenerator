package com.staging.sg.common.repository;

import com.staging.sg.common.entity.DmasKek;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DmasKekRepository extends JpaRepository<DmasKek, Long> {
    Optional<DmasKek> findByMemberGroupId(String memberGroupId);
    Optional<DmasKek> findByMemberGroupIdAndStatus(String memberGroupId, String status);
}
