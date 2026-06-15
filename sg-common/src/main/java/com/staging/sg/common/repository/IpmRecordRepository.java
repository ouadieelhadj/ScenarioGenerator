package com.staging.sg.common.repository;

import com.staging.sg.common.entity.IpmRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface IpmRecordRepository extends JpaRepository<IpmRecord, Long> {
    List<IpmRecord> findByIpmFileId(Long ipmFileId);
    List<IpmRecord> findByIpmFileIdAndMti(Long ipmFileId, String mti);
    List<IpmRecord> findByIpmFileIdAndRecordType(Long ipmFileId, String recordType);
    long countByIpmFileId(Long ipmFileId);
}
