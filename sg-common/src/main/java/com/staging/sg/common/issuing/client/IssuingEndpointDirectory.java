package com.staging.sg.common.issuing.client;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class IssuingEndpointDirectory {
    private static final String SQL = """
            SELECT issuer_id, interface_type, protocol, host, port, base_path,
                   connect_timeout_ms, read_timeout_ms
              FROM issuing_interface_endpoint
             WHERE interface_type = ?
               AND direction = 'INBOUND'
               AND status = 'ACTIVE'
               AND (CAST(? AS VARCHAR) IS NULL OR issuer_id = ?)
             ORDER BY interface_version DESC
            """;

    private final JdbcTemplate jdbc;

    public IssuingEndpointDirectory(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public IssuingEndpoint requireActive(
            String interfaceType, String issuerId) {
        String normalizedIssuer = issuerId == null || issuerId.isBlank()
                ? null : issuerId;
        List<IssuingEndpoint> matches = jdbc.query(
                SQL,
                (rs, row) -> new IssuingEndpoint(
                        rs.getString("issuer_id"),
                        rs.getString("interface_type"),
                        rs.getString("protocol"),
                        rs.getString("host"),
                        rs.getInt("port"),
                        rs.getString("base_path"),
                        rs.getInt("connect_timeout_ms"),
                        rs.getInt("read_timeout_ms")),
                interfaceType, normalizedIssuer, normalizedIssuer);
        if (matches.isEmpty()) {
            throw new IllegalStateException(
                    "No active database issuing endpoint for " + interfaceType);
        }
        if (normalizedIssuer == null && matches.size() != 1) {
            throw new IllegalStateException(
                    "issuerId is required when several issuing endpoints are active");
        }
        return matches.getFirst();
    }
}
