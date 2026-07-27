package com.staging.sg.swam.lis.member.repository;

import com.staging.sg.swam.lis.member.persistence.MemberBusinessDay;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface MemberBusinessDayRepository extends JpaRepository<MemberBusinessDay, Long> {
    Optional<MemberBusinessDay> findByBankMemberIdAndBusinessDate(String bankMemberId, LocalDate businessDate);
}
