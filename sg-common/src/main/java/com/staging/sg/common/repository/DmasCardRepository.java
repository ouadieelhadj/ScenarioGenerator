package com.staging.sg.common.repository;

import com.staging.sg.common.entity.DmasCard;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DmasCardRepository extends JpaRepository<DmasCard, Long> {
    Optional<DmasCard> findByPan(String pan);
}
