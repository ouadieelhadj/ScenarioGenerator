package com.staging.sg.mc.sms.acquirer.repository;

import com.staging.sg.mc.sms.acquirer.entity.McSmsAcqKey;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface McSmsAcqKeyRepository extends JpaRepository<McSmsAcqKey, Long> {
    Optional<McSmsAcqKey> findByMemberGroupIdAndKeyTypeAndStatus(
            String memberGroupId, String keyType, String status);
}
