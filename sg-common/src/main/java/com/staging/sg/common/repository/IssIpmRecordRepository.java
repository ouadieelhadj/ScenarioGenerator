package com.staging.sg.common.repository;

import com.staging.sg.common.entity.IssIpmRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface IssIpmRecordRepository extends JpaRepository<IssIpmRecord, Long> {
    List<IssIpmRecord> findByIpmFileId(Long ipmFileId);
    List<IssIpmRecord> findByIpmFileIdAndDirection(Long ipmFileId, String direction);
    List<IssIpmRecord> findByMti(String mti);
}
