package com.staging.sg.common.repository;

import com.staging.sg.common.entity.CampaignExecutionResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface CampaignExecutionResultRepository extends JpaRepository<CampaignExecutionResult, Long> {
    List<CampaignExecutionResult> findByExecutionId(Long executionId);
}
