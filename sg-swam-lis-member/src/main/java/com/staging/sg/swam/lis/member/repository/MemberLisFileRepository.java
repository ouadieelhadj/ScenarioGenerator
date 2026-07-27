package com.staging.sg.swam.lis.member.repository;

import com.staging.sg.swam.lis.common.model.LisDirection;
import com.staging.sg.swam.lis.member.persistence.MemberLisFile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MemberLisFileRepository extends JpaRepository<MemberLisFile, Long> {
    Optional<MemberLisFile> findByDirectionAndSha256(LisDirection direction, String sha256);
}
