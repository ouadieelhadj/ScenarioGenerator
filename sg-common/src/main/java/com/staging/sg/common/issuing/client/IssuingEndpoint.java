package com.staging.sg.common.issuing.client;

public record IssuingEndpoint(
        String issuerId,
        String interfaceType,
        String protocol,
        String host,
        int port,
        String basePath,
        int connectTimeoutMs,
        int readTimeoutMs) {

    public IssuingEndpoint {
        if (blank(issuerId) || blank(interfaceType) || blank(protocol)
                || blank(host) || port < 1 || port > 65535
                || connectTimeoutMs < 1 || readTimeoutMs < 1) {
            throw new IllegalArgumentException("Invalid issuing endpoint");
        }
    }

    public String baseUrl() {
        String scheme = switch (protocol) {
            case "REST" -> "http";
            case "REST_TLS" -> "https";
            default -> throw new IllegalStateException(
                    "Issuing endpoint protocol must be REST or REST_TLS");
        };
        String normalizedPath = blank(basePath)
                ? "/api/issuing/v1"
                : (basePath.startsWith("/") ? basePath : "/" + basePath);
        return scheme + "://" + host + ":" + port + normalizedPath;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
