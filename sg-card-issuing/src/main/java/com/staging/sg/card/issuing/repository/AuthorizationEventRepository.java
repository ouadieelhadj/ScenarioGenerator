package com.staging.sg.card.issuing.repository;

import com.staging.sg.card.issuing.domain.AuthorizationEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface AuthorizationEventRepository extends JpaRepository<AuthorizationEvent,UUID> {}
