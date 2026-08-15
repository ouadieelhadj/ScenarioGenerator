package com.staging.sg.fraud.repository;
import com.staging.sg.fraud.domain.ThreatSignal; import org.springframework.data.jpa.repository.JpaRepository; import java.util.UUID;
public interface ThreatSignalRepository extends JpaRepository<ThreatSignal,UUID>{}
