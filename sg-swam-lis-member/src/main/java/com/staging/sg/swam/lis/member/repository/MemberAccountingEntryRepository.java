package com.staging.sg.swam.lis.member.repository;
import com.staging.sg.swam.lis.member.persistence.MemberAccountingEntry;
import org.springframework.data.jpa.repository.JpaRepository;
public interface MemberAccountingEntryRepository extends JpaRepository<MemberAccountingEntry,Long>{
 boolean existsByEntryKey(String entryKey);
}
