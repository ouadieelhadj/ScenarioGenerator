package com.staging.sg.common.repository;

import com.staging.sg.common.entity.BinRange;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BinRangeRepository extends JpaRepository<BinRange, Long> {
    List<BinRange> findByEnabledTrue();
}
