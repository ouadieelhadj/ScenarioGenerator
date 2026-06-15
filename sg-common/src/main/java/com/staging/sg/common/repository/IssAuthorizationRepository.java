package com.staging.sg.common.repository;

import com.staging.sg.common.entity.IssAuthorization;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface IssAuthorizationRepository extends JpaRepository<IssAuthorization, Long> {
    Optional<IssAuthorization> findByDe011Stan(String stan);
    Optional<IssAuthorization> findByDe037Rrn(String rrn);
    List<IssAuthorization> findByApprovedTrue();
    List<IssAuthorization> findByDe002Pan(String pan);
    List<IssAuthorization> findAllByOrderByReceivedAtDesc();
}
