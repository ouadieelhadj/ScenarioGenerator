package com.staging.sg.switchlab.contracts;

import java.util.List;

public record SwitchLabPosScenarioDefinition(
        String code,
        String label,
        String objective,
        String classification,
        boolean requiresCertificationCard,
        List<String> expectedResults) {
}
