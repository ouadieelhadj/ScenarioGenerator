package com.staging.sg.acquiring.repository;

import com.staging.sg.acquiring.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface AcquiringProductBindingRepository extends JpaRepository<AcquiringProductBinding, UUID> {
    @Query("select b from AcquiringProductBinding b where b.acquirerId = :acquirerId "
            + "and b.usage = :usage and b.currency = :currency and b.active = true "
            + "and b.validFrom <= :at and (b.validTo is null or b.validTo > :at) "
            + "and (b.channel = :channel or b.channel = com.staging.sg.acquiring.domain.AcceptanceChannel.BOTH)")
    List<AcquiringProductBinding> resolve(@Param("acquirerId") String acquirerId,
            @Param("usage") ProductBindingUsage usage,
            @Param("channel") AcceptanceChannel channel,
            @Param("currency") String currency, @Param("at") Instant at);
}
