package com.staging.sg.mc.sms.issuer.repository;

import com.staging.sg.mc.sms.issuer.entity.McSmsCard;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface McSmsCardRepository extends JpaRepository<McSmsCard, Long> {
    Optional<McSmsCard> findByPanAndStatus(String pan, String status);
}
