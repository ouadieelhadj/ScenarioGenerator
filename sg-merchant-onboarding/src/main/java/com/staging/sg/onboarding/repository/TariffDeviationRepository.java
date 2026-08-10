package com.staging.sg.onboarding.repository;
import com.staging.sg.onboarding.domain.TariffDeviation;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
public interface TariffDeviationRepository extends JpaRepository<TariffDeviation, UUID> {}
