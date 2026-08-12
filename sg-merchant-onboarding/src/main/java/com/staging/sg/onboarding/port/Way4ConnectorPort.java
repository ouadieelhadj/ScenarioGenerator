package com.staging.sg.onboarding.port;

import java.util.List;
import java.util.UUID;

public interface Way4ConnectorPort {
    Result generate(PortalWay4ExportCommand command, String correlationId);
    Result generateBatch(List<PortalWay4ExportCommand> commands, String idempotencyKey,
            String correlationId);
    record Result(UUID fileId, String fileName, String status, String xmlSha256,
            String xsdSha256, String xml) {
        public Result(UUID fileId, String status) { this(fileId,null,status,null,null,null); }
    }
}
