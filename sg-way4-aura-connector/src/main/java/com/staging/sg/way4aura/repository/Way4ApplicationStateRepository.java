package com.staging.sg.way4aura.repository;
import com.staging.sg.way4aura.domain.Way4ApplicationState;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface Way4ApplicationStateRepository extends JpaRepository<Way4ApplicationState, UUID> {
    Optional<Way4ApplicationState> findBySourceTypeAndSourceId(String sourceType, UUID sourceId);
}
