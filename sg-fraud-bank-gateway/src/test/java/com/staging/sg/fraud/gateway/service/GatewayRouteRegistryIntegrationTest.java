package com.staging.sg.fraud.gateway.service;

import com.staging.sg.fraud.gateway.domain.*;
import com.staging.sg.fraud.gateway.repository.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import static org.assertj.core.api.Assertions.*;

@DataJpaTest
@Import(GatewayRouteRegistry.class)
class GatewayRouteRegistryIntegrationTest {
    @Autowired GatewayMemberRepository members;
    @Autowired GatewayMemberSectorRepository sectors;
    @Autowired GatewayConnectionProfileRepository profiles;
    @Autowired GatewayRouteRegistry routes;

    @BeforeEach
    void seed() {
        members.save(new GatewayMember("MEMBER-OUADIE", "Ouadie Bank", true));
        members.save(new GatewayMember("MEMBER-TRESOR", "Tresor Bank", true));
        sectors.save(new GatewayMemberSector("MEMBER-OUADIE", "MONETIQUE", "Monétique", true));
        sectors.save(new GatewayMemberSector("MEMBER-OUADIE", "MOBILE_BANKING", "Mobile Banking", true));
        sectors.save(new GatewayMemberSector("MEMBER-TRESOR", "MONETIQUE", "Monétique", true));
        profiles.saveAndFlush(profile("OUADIE-ISO", "MEMBER-OUADIE", "MONETIQUE", "ISO8583", 8601));
        profiles.saveAndFlush(profile("OUADIE-REST", "MEMBER-OUADIE", null, "REST", 8701));
    }

    @Test
    void resolvesMemberFromDedicatedPortAndValidatesItsSector() {
        GatewayConnectionProfile profile = routes.requireActiveRoute("REST", 8701);
        assertThat(profile.memberId()).isEqualTo("MEMBER-OUADIE");
        routes.requireActiveSector(profile.memberId(), "MOBILE_BANKING");
        assertThatThrownBy(() -> routes.requireActiveSector(profile.memberId(), "UNKNOWN"))
                .isInstanceOf(GatewayRouteRegistry.InactiveGatewayRouteException.class);
    }

    @Test
    void sameProtocolCannotReuseAnotherBanksPort() {
        profiles.save(profile("TRESOR-REST", "MEMBER-TRESOR", null, "REST", 8701));
        assertThatThrownBy(profiles::flush).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void unknownPortCannotSelectMemberFromPayload() {
        assertThatThrownBy(() -> routes.requireActiveRoute("REST", 8799))
                .isInstanceOf(GatewayRouteRegistry.UnknownGatewayRouteException.class);
    }

    private GatewayConnectionProfile profile(String code, String memberId, String sectorId,
            String protocol, int port) {
        return new GatewayConnectionProfile(code, memberId, sectorId, protocol, "SERVER", port,
                null, null, protocol.equals("ISO8583") ? "ISO87A" : "REST-V1",
                code + "-CREDENTIAL", protocol.equals("ISO8583") ? code + "-ZMK" : null,
                30, 5, true);
    }
}
