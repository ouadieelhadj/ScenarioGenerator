package com.staging.sg.common.repository;

import com.staging.sg.common.entity.McDmasCard;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface McDmasCardRepository extends JpaRepository<McDmasCard, Long> {
    Optional<McDmasCard> findByPan(String pan);
}
