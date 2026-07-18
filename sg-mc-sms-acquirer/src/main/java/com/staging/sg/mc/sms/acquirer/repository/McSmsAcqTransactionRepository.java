package com.staging.sg.mc.sms.acquirer.repository;

import com.staging.sg.mc.sms.acquirer.entity.McSmsAcqTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface McSmsAcqTransactionRepository extends JpaRepository<McSmsAcqTransaction, Long> {
    List<McSmsAcqTransaction> findByPanOrderByCreatedAtDesc(String pan);
}
