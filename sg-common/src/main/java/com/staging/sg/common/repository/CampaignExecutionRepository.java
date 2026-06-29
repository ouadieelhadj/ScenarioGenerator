package com.staging.sg.common.repository;

import com.staging.sg.common.entity.CampaignExecution;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CampaignExecutionRepository extends JpaRepository<CampaignExecution, Long> {
    List<CampaignExecution> findByCampaignIdOrderByIdDesc(Long campaignId);
}
