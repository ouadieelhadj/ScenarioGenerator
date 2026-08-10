package com.staging.sg.onboarding.repository;
import com.staging.sg.onboarding.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface PricingPackVersionRepository extends JpaRepository<PricingPackVersion, UUID> {
    boolean existsByPackCodeAndVersionNumber(String packCode, int versionNumber);
    List<PricingPackVersion> findByPackCodeOrderByVersionNumberDesc(String packCode);
    List<PricingPackVersion> findByPackCodeAndStatus(String packCode, PricingPackStatus status);
    Optional<PricingPackVersion> findByPackCodeAndVersionNumber(String packCode, int versionNumber);
}
