package com.staging.sg.switchlab.contracts;

public record SwitchLabEnvironmentReference(
        String id,
        String code,
        String label,
        String type,
        boolean active) {
}
