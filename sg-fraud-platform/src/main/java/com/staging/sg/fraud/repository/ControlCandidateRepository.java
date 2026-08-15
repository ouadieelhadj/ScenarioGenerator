package com.staging.sg.fraud.repository;
import com.staging.sg.fraud.domain.ControlCandidate; import org.springframework.data.jpa.repository.JpaRepository; import java.util.UUID;
public interface ControlCandidateRepository extends JpaRepository<ControlCandidate,UUID>{}
