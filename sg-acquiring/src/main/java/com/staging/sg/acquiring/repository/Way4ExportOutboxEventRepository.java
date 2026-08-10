package com.staging.sg.acquiring.repository;
import com.staging.sg.acquiring.domain.Way4ExportOutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.*;
public interface Way4ExportOutboxEventRepository extends JpaRepository<Way4ExportOutboxEvent, UUID> {
    Optional<Way4ExportOutboxEvent> findByIdempotencyKey(String idempotencyKey);
    @Query(value = """
        select * from acquiring_way4_export_outbox
         where status = 'PENDING' and available_at <= :now and attempts < 8
         order by created_at limit :limit for update skip locked
        """, nativeQuery = true)
    List<Way4ExportOutboxEvent> lockDispatchable(@Param("now") Instant now, @Param("limit") int limit);
    @Modifying
    @Query(value = """
        update acquiring_way4_export_outbox
           set status = 'PENDING', available_at = :now, locked_by = null,
               locked_at = null, lease_until = null, updated_at = :now, version = version + 1
         where status = 'PROCESSING' and lease_until <= :now
        """, nativeQuery = true)
    int recoverExpiredLeases(@Param("now") Instant now);
}
