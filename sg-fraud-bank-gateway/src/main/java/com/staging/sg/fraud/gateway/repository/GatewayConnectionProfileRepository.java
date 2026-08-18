package com.staging.sg.fraud.gateway.repository;

import com.staging.sg.fraud.gateway.domain.GatewayConnectionProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface GatewayConnectionProfileRepository extends JpaRepository<GatewayConnectionProfile, UUID> {
    Optional<GatewayConnectionProfile> findByProtocolAndListenPortAndActiveTrue(String protocol, int listenPort);
    List<GatewayConnectionProfile> findByProtocolAndActiveTrueOrderByListenPort(String protocol);
    List<GatewayConnectionProfile> findByMemberIdAndActiveTrueOrderByProtocolAscListenPortAsc(String memberId);
}
