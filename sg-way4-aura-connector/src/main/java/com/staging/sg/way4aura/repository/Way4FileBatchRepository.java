package com.staging.sg.way4aura.repository;
import com.staging.sg.way4aura.domain.Way4FileBatch;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface Way4FileBatchRepository extends JpaRepository<Way4FileBatch, UUID> {
    Optional<Way4FileBatch> findByIdempotencyKey(String idempotencyKey);
}
