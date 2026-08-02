package com.staging.sg.deployment.model;

public record DatabaseConfig(
        DatabaseType type,
        String host,
        Integer port,
        String database,
        String schema,
        String user,
        String passwordSecretRef,
        String oracleServiceName,
        String oracleSid
) {
    public static DatabaseConfig none() {
        return new DatabaseConfig(DatabaseType.NONE, null, null, null, null,
                null, null, null, null);
    }
}
