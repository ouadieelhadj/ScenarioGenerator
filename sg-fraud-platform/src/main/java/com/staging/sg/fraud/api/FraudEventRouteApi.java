package com.staging.sg.fraud.api;

import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.UUID;

public final class FraudEventRouteApi {
    private FraudEventRouteApi() {}

    public record RouteRequest(
            @NotBlank @Size(max = 249) String topicTemplate,
            @NotBlank @Pattern(regexp = "[A-Za-z0-9._-]{1,32}") String schemaVersion,
            @NotBlank @Pattern(regexp = "[A-Z0-9_-]{2,32}") String retentionClass,
            boolean enabled,
            @Min(0) @Max(1000) int priority) {}

    public record RouteResponse(UUID id, String memberId, String sectorId, String eventType,
            String topicTemplate, String schemaVersion, String retentionClass, boolean enabled,
            int priority, Instant updatedAt) {}
}
