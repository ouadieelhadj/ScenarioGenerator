package com.staging.sg.mc.sms.issuer.repository;

import com.staging.sg.mc.sms.issuer.entity.McSmsIssTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface McSmsIssTransactionRepository extends JpaRepository<McSmsIssTransaction, Long> {
    Optional<McSmsIssTransaction> findByStanAndTransmissionDt(String stan, String transmissionDt);
}
