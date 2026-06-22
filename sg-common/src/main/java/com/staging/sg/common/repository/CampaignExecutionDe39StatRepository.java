package com.staging.sg.common.repository;

import com.staging.sg.common.entity.CampaignExecutionDe39Stat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface CampaignExecutionDe39StatRepository extends JpaRepository<CampaignExecutionDe39Stat, Long> {
    List<CampaignExecutionDe39Stat> findByExecutionId(Long executionId);
}
