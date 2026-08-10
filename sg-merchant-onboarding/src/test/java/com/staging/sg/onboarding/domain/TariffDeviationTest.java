package com.staging.sg.onboarding.domain;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class TariffDeviationTest {
    @Test
    void requiresDifferentMakerAndCheckerAndPreservesSnapshots() {
        TariffDeviation value = TariffDeviation.request(UUID.randomUUID(), "PACK", 1,
                "{\"fee\":10}", "{\"fee\":8}", "commercial decision", "maker");
        assertThrows(IllegalStateException.class, () -> value.approve("maker"));
        value.approve("checker");
        assertEquals(TariffDeviationStatus.APPROVED, value.status());
        assertEquals("{\"fee\":10}", value.beforeJson());
        assertEquals("{\"fee\":8}", value.afterJson());
    }

    @Test
    void activatedPricingVersionIsImmutableAndCanBeRetired() {
        PricingPackVersion value = PricingPackVersion.draft("PACK", 1,
                "{\"annex\":\"approved\"}", "maker");
        value.activate("checker");
        assertEquals(PricingPackStatus.ACTIVE, value.status());
        assertThrows(IllegalStateException.class, () -> value.activate("other"));
        value.retire();
        assertEquals(PricingPackStatus.RETIRED, value.status());
    }
}
