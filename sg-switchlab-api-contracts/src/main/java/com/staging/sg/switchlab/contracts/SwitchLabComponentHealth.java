package com.staging.sg.switchlab.contracts;

import java.time.Instant;
import java.util.List;

public record SwitchLabComponentHealth(
        String code,
        String status,
        Instant checkedAt,
        List<String> capabilities,
        List<SwitchLabAuthorizedAction> actions) {
}
