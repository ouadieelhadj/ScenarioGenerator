package com.staging.sg.common.repository;

import com.staging.sg.common.entity.SwamAcquirerCard;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SwamAcquirerCardRepository extends JpaRepository<SwamAcquirerCard, Long> {
    Optional<SwamAcquirerCard> findByPan(String pan);
}
