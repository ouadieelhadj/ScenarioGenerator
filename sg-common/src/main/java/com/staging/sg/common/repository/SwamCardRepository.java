package com.staging.sg.common.repository;

import com.staging.sg.common.entity.SwamCard;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface SwamCardRepository extends JpaRepository<SwamCard, Long> {
    Optional<SwamCard> findByPan(String pan);
}
