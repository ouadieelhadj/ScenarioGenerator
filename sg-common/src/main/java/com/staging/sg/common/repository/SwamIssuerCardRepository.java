package com.staging.sg.common.repository;

import com.staging.sg.common.entity.SwamIssuerCard;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SwamIssuerCardRepository extends JpaRepository<SwamIssuerCard, Long> {
    Optional<SwamIssuerCard> findByPan(String pan);
}
