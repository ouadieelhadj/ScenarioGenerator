package com.staging.sg.common.repository;

import com.staging.sg.common.entity.GeneratedTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GeneratedTransactionRepository extends JpaRepository<GeneratedTransaction, Long> {
    List<GeneratedTransaction> findByCampaignId(Long campaignId);
    long countByCampaignId(Long campaignId);
    void deleteByCampaignId(Long campaignId);
}
