package com.staging.sg.onboarding.repository;

import com.staging.sg.onboarding.domain.WorkflowApprovalRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WorkflowApprovalRequestRepository extends JpaRepository<WorkflowApprovalRequest, Long> {
    List<WorkflowApprovalRequest> findByCreatedByOrderByCreatedAtDesc(String createdBy);
    List<WorkflowApprovalRequest> findByStatusOrderByCreatedAtAsc(String status);
}
