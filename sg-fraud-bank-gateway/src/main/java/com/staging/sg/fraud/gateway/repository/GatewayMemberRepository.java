package com.staging.sg.fraud.gateway.repository;

import com.staging.sg.fraud.gateway.domain.GatewayMember;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GatewayMemberRepository extends JpaRepository<GatewayMember, String> {}
