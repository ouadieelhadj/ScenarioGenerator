package com.staging.sg.switchlab.contracts;

public record SwitchLabApiError(
        String schemaVersion,
        String code,
        String message,
        String correlationId) {
}
