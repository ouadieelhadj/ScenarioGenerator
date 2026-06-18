package com.staging.sg.common.repository;

import com.staging.sg.common.entity.AcqIpmRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface AcqIpmRecordRepository extends JpaRepository<AcqIpmRecord, Long> {
    List<AcqIpmRecord> findByIpmFileId(Long ipmFileId);
    List<AcqIpmRecord> findByIpmFileIdAndDirection(Long ipmFileId, String direction);
    List<AcqIpmRecord> findByMti(String mti);
}
