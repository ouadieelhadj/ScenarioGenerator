package com.staging.sg.common.repository;

import com.staging.sg.common.entity.CampaignLoadStep;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CampaignLoadStepRepository extends JpaRepository<CampaignLoadStep, Long> {
    List<CampaignLoadStep> findByCampaignIdOrderByStepOrderAsc(Long campaignId);
}
