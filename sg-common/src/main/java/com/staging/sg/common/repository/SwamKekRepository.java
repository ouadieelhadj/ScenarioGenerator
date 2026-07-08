package com.staging.sg.common.repository;

import com.staging.sg.common.entity.SwamKek;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface SwamKekRepository extends JpaRepository<SwamKek, Long> {
    Optional<SwamKek> findByMemberGroupId(String memberGroupId);
}
