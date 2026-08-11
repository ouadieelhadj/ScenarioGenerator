package com.staging.sg.onboarding.port;

import java.util.UUID;

public interface Way4ConnectorPort {
    Result generate(PortalWay4ExportCommand command, String correlationId);
    record Result(UUID fileId, String status) {}
}
