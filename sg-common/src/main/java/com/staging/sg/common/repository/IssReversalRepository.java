package com.staging.sg.common.repository;

import com.staging.sg.common.entity.IssReversal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface IssReversalRepository extends JpaRepository<IssReversal, Long> {
    List<IssReversal> findByIssAuthorizationId(Long issAuthId);
    List<IssReversal> findAllByOrderByReceivedAtDesc();
}
