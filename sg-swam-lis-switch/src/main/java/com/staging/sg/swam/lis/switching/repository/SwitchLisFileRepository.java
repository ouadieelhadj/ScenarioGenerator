package com.staging.sg.swam.lis.switching.repository;

import com.staging.sg.swam.lis.common.model.LisDirection;
import com.staging.sg.swam.lis.switching.persistence.SwitchLisFile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SwitchLisFileRepository extends JpaRepository<SwitchLisFile, Long> {
    Optional<SwitchLisFile> findByDirectionAndSha256(LisDirection direction, String sha256);
}
