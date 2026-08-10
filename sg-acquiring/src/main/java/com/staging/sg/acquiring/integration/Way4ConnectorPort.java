package com.staging.sg.acquiring.integration;
import java.util.UUID;
public interface Way4ConnectorPort {
    Result generate(Way4ExportRequest request);
    record Result(UUID fileId, String status) {}
}
