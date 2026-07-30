package com.staging.sg.waypos.server.repository;

import com.staging.sg.waypos.server.domain.PosBinRoute;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PosBinRouteRepository extends JpaRepository<PosBinRoute, Long> {
    List<PosBinRoute> findByActiveTrueOrderByPriorityDesc();
}
