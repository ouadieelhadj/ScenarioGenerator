package com.staging.sg.mc.sms.acquirer.repository;

import com.staging.sg.mc.sms.acquirer.entity.McSmsKek;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface McSmsKekRepository extends JpaRepository<McSmsKek, Long> {
    Optional<McSmsKek> findByMemberGroupId(String memberGroupId);
}
