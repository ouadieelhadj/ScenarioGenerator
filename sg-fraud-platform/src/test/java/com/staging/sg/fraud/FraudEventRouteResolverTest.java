package com.staging.sg.fraud;

import com.staging.sg.fraud.domain.FraudEventOutbox;
import com.staging.sg.fraud.domain.FraudEventRoute;
import com.staging.sg.fraud.repository.FraudEventRouteRepository;
import com.staging.sg.fraud.service.FraudEventRouteResolver;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FraudEventRouteResolverTest {
    private final FraudEventRouteRepository repository = mock(FraudEventRouteRepository.class);

    @Test
    void resolvesExactDatabaseRouteAndMetadata() {
        FraudEventRoute route = FraudEventRoute.create("OUADIE_BANK", "MONETIQUE", "RISK_ASSESSMENT_COMPLETED",
                "fraud.{memberId}.{sectorId}.assessment.v2", "v2", "REGULATORY", true, 10);
        when(repository.findByMemberIdAndSectorIdAndEventType("OUADIE_BANK", "MONETIQUE", "RISK_ASSESSMENT_COMPLETED"))
                .thenReturn(Optional.of(route));
        var resolver = new FraudEventRouteResolver(repository,
                "fraud.{memberId}.{sectorId}.fallback.v1", true);

        var decision = resolver.resolve(event());

        assertEquals("fraud.ouadie_bank.monetique.assessment.v2", decision.topic());
        assertEquals("v2", decision.schemaVersion());
        assertEquals("DATABASE", decision.source());
    }

    @Test
    void productionFailsClosedWhenNoEnabledRouteExists() {
        when(repository.findByMemberIdAndSectorIdAndEventType(anyString(), anyString(), anyString()))
                .thenReturn(Optional.empty());
        var resolver = new FraudEventRouteResolver(repository,
                "fraud.{memberId}.{sectorId}.fallback.v1", true);
        assertThrows(IllegalStateException.class, () -> resolver.resolve(event()));
    }

    @Test
    void laboratoryFallbackRemainsBackwardCompatible() {
        when(repository.findByMemberIdAndSectorIdAndEventType(anyString(), anyString(), anyString()))
                .thenReturn(Optional.empty());
        var resolver = new FraudEventRouteResolver(repository,
                "fraud.{memberId}.{sectorId}.fallback.v1", false);
        assertEquals("fraud.ouadie_bank.monetique.fallback.v1", resolver.resolve(event()).topic());
    }

    @Test
    void rejectsRouteThatEscapesMemberSectorNamespace() {
        var resolver = new FraudEventRouteResolver(repository,
                "fraud.{memberId}.{sectorId}.fallback.v1", false);
        assertThrows(IllegalArgumentException.class,
                () -> resolver.validateTemplate("fraud.shared.assessment.v1", "OUADIE_BANK", "MONETIQUE"));
    }

    private FraudEventOutbox event() {
        return FraudEventOutbox.pending("OUADIE_BANK", "MONETIQUE", "ASSESSMENT", "assessment-1",
                "RISK_ASSESSMENT_COMPLETED", "{}");
    }
}
