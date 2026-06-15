package com.staging.sg.common.repository;

import com.staging.sg.common.entity.IssAdvice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface IssAdviceRepository extends JpaRepository<IssAdvice, Long> {
    List<IssAdvice> findByIssAuthorizationId(Long issAuthId);
    List<IssAdvice> findAllByOrderByReceivedAtDesc();
}
