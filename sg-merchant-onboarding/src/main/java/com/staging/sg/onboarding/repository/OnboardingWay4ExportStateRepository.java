package com.staging.sg.onboarding.repository;
import com.staging.sg.onboarding.domain.OnboardingWay4ExportState;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
import java.util.List;
public interface OnboardingWay4ExportStateRepository extends JpaRepository<OnboardingWay4ExportState,UUID>{
    List<OnboardingWay4ExportState> findByStatusInOrderByUpdatedAtAsc(List<String> statuses);
}
