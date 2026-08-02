package com.staging.sg.deployment.catalog;

import com.staging.sg.deployment.model.ModuleSide;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ModuleCatalogVisaTest {
    @Test
    void exposesTheTwoVisaMembersAndTheirTwoNetworkSimulators() {
        ModuleCatalog catalog = ModuleCatalog.scenarioGenerator();

        assertEquals(ModuleSide.MEMBER, catalog.find("VISA_ONLINE_MEMBER").orElseThrow().side());
        assertTrue(catalog.find("VISA_BASE2_MEMBER").orElseThrow().requiredVariables()
                .contains("VISA_BASE2_ACQUIRING_IDENTIFIER"));
        assertEquals(ModuleSide.SIMULATOR,
                catalog.find("VISANET_NETWORK_SIMULATOR").orElseThrow().side());
        assertTrue(catalog.find("VISA_BASE2_NETWORK_SIMULATOR").isPresent());
    }
}
