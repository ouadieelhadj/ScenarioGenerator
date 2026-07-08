package com.staging.sg.common.repository;

import com.staging.sg.common.entity.NetworkRef;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NetworkRepository extends JpaRepository<NetworkRef, Long> {
    Optional<NetworkRef> findByCode(String code);
    List<NetworkRef> findByActiveTrue();
    boolean existsByCode(String code);
}
