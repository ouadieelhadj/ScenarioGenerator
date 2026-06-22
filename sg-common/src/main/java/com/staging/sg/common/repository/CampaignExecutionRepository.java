package com.staging.sg.common.repository;

import com.staging.sg.common.entity.CampaignExecution;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface CampaignExecutionRepository extends JpaRepository<CampaignExecution, Long> {
    List<CampaignExecution> findByCampaignIdOrderByStartedAtDesc(Long campaignId);
}
