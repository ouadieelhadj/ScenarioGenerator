package com.staging.sg.common.repository;

import com.staging.sg.common.entity.Test;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TestRepository extends JpaRepository<Test, Long> {

    List<Test> findByActiveTrue();

    @Query("SELECT t FROM Test t JOIN t.assignedUsers u WHERE u.id = :userId AND t.active = true")
    List<Test> findByAssignedUserId(@Param("userId") Long userId);
}
