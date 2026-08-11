package com.staging.sg.way4aura.repository;
import com.staging.sg.way4aura.domain.Way4MidAllocation;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface Way4MidAllocationRepository extends JpaRepository<Way4MidAllocation,UUID>{
    Optional<Way4MidAllocation> findByOnboardingCaseId(UUID onboardingCaseId);
}
