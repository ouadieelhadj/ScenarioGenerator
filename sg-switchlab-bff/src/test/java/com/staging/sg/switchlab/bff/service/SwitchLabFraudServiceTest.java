package com.staging.sg.switchlab.bff.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SwitchLabFraudServiceTest {
    @Test
    void remainsFailClosedWhenFraudPlatformIsNotConfigured() {
        var overview = new SwitchLabFraudService("").overview();

        assertEquals("SWITCHLAB", overview.workspace());
        assertEquals("LAB_ALERT_ONLY", overview.operatingMode());
        assertEquals("UNKNOWN", overview.overallStatus());
        assertFalse(overview.platformConfigured());
        assertFalse(overview.features().isEmpty());
        assertTrue(overview.features().stream().noneMatch(feature -> feature.available()));
        assertTrue(overview.features().stream()
                .allMatch(feature -> "UNAVAILABLE".equals(feature.status())));
    }
}
