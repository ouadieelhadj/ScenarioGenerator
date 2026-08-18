package com.staging.sg.fraud.gateway.service;

import com.staging.sg.fraud.gateway.domain.GatewayConnectionProfile;
import com.staging.sg.fraud.gateway.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
public class GatewayRouteRegistry {
    public static final String ISO8583 = "ISO8583";
    public static final String REST = "REST";
    private final GatewayMemberRepository members;
    private final GatewayMemberSectorRepository sectors;
    private final GatewayConnectionProfileRepository profiles;

    public GatewayRouteRegistry(GatewayMemberRepository members, GatewayMemberSectorRepository sectors,
            GatewayConnectionProfileRepository profiles) {
        this.members = members; this.sectors = sectors; this.profiles = profiles;
    }

    @Transactional(readOnly = true)
    public GatewayConnectionProfile requireActiveRoute(String protocol, int localPort) {
        GatewayConnectionProfile profile = profiles.findByProtocolAndListenPortAndActiveTrue(protocol, localPort)
                .orElseThrow(() -> new UnknownGatewayRouteException(protocol, localPort));
        if (members.findById(profile.memberId()).filter(m -> m.active()).isEmpty()
                || profile.sectorId() != null
                && !sectors.existsByMemberIdAndSectorIdAndActiveTrue(profile.memberId(), profile.sectorId())) {
            throw new InactiveGatewayRouteException(profile.connectionCode());
        }
        return profile;
    }

    @Transactional(readOnly = true)
    public List<GatewayConnectionProfile> activeProfiles(String protocol) {
        return profiles.findByProtocolAndActiveTrueOrderByListenPort(protocol).stream()
                .filter(profile -> members.findById(profile.memberId()).filter(m -> m.active()).isPresent())
                .filter(profile -> profile.sectorId() == null
                        || sectors.existsByMemberIdAndSectorIdAndActiveTrue(profile.memberId(), profile.sectorId()))
                .toList();
    }

    @Transactional(readOnly = true)
    public void requireActiveSector(String memberId, String sectorId) {
        if (!sectors.existsByMemberIdAndSectorIdAndActiveTrue(memberId, sectorId)) {
            throw new InactiveGatewayRouteException(memberId + "/" + sectorId);
        }
    }

    public static final class UnknownGatewayRouteException extends RuntimeException {
        public UnknownGatewayRouteException(String protocol, int port) { super("No active " + protocol + " route on port " + port); }
    }
    public static final class InactiveGatewayRouteException extends RuntimeException {
        public InactiveGatewayRouteException(String code) { super("Inactive member or sector for route " + code); }
    }
}
