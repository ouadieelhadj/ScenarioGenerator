package com.staging.sg.member.contracts;

public record SwitchApiError(
        String schemaVersion,
        String code,
        String message,
        String correlationId) {
}
