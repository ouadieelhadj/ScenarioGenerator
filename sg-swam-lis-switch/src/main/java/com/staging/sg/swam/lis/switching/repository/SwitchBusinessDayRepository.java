package com.staging.sg.swam.lis.switching.repository;

import com.staging.sg.swam.lis.switching.persistence.SwitchBusinessDay;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface SwitchBusinessDayRepository extends JpaRepository<SwitchBusinessDay, Long> {
    Optional<SwitchBusinessDay> findByBankMemberIdAndBusinessDate(String bankMemberId, LocalDate businessDate);
}
