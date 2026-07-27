package com.staging.sg.swam.lis.switching.repository;
import com.staging.sg.swam.lis.switching.persistence.SwitchAccountingEntry;
import org.springframework.data.jpa.repository.JpaRepository;
public interface SwitchAccountingEntryRepository extends JpaRepository<SwitchAccountingEntry,Long>{
 boolean existsByEntryKey(String entryKey);
}
