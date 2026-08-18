package com.staging.sg.fraud.gateway.config;

import com.staging.sg.fraud.gateway.domain.*;
import com.staging.sg.fraud.gateway.repository.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.Transactional;

@Configuration
@ConditionalOnProperty(name = "fraud-gateway.lab.multibank-bootstrap-enabled", havingValue = "true")
public class FraudLabMultibankBootstrap {
    @Bean
    CommandLineRunner fraudLabBanks(GatewayMemberRepository members,
            GatewayMemberSectorRepository sectors, GatewayConnectionProfileRepository profiles) {
        return args -> seed(members, sectors, profiles);
    }

    @Transactional
    void seed(GatewayMemberRepository members, GatewayMemberSectorRepository sectors,
            GatewayConnectionProfileRepository profiles) {
        seedBank(members, sectors, profiles, "MEMBER-OUADIE", "Ouadie Bank", 8601, 8701);
        seedBank(members, sectors, profiles, "MEMBER-TRESOR", "Tresor Bank", 8602, 8702);
        seedBank(members, sectors, profiles, "MEMBER-SEDIK", "Sedik Bank", 8603, 8703);
    }

    private void seedBank(GatewayMemberRepository members, GatewayMemberSectorRepository sectors,
            GatewayConnectionProfileRepository profiles, String memberId, String name, int isoPort, int restPort) {
        if (!members.existsById(memberId)) members.save(new GatewayMember(memberId, name, true));
        if (!sectors.existsByMemberIdAndSectorIdAndActiveTrue(memberId, "MONETIQUE"))
            sectors.save(new GatewayMemberSector(memberId, "MONETIQUE", "Monétique", true));
        if (!sectors.existsByMemberIdAndSectorIdAndActiveTrue(memberId, "MOBILE_BANKING"))
            sectors.save(new GatewayMemberSector(memberId, "MOBILE_BANKING", "Mobile Banking", true));
        if (profiles.findByProtocolAndListenPortAndActiveTrue("ISO8583", isoPort).isEmpty())
            profiles.save(new GatewayConnectionProfile(memberId + "-ISO", memberId, "MONETIQUE", "ISO8583",
                    "SERVER", isoPort, null, null, "ISO87A", memberId + "-ISO-CREDENTIAL",
                    memberId + "-ZMK", 30, 5, true));
        if (profiles.findByProtocolAndListenPortAndActiveTrue("REST", restPort).isEmpty())
            profiles.save(new GatewayConnectionProfile(memberId + "-REST", memberId, null, "REST",
                    "SERVER", restPort, null, null, "REST-V1", memberId + "-REST-CREDENTIAL",
                    null, 30, 5, true));
    }
}
