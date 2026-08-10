package com.staging.sg.way4aura.repository;

import com.staging.sg.way4aura.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.*;

public interface AuraBindingRepository extends JpaRepository<AuraBinding, UUID> {
    @Query("""
        select b from AuraBinding b where b.type = :type and b.sourceCode = :source
          and b.active = true and b.validFrom <= :at and (b.validTo is null or b.validTo > :at)
        order by b.bindingVersion desc
        """)
    List<AuraBinding> resolve(@Param("type") AuraBindingType type,
            @Param("source") String source, @Param("at") Instant at);
}
