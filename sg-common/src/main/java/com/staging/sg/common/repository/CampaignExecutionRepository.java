package com.staging.sg.common.repository;

import com.staging.sg.common.entity.CampaignExecution;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CampaignExecutionRepository extends JpaRepository<CampaignExecution, Long> {
}
