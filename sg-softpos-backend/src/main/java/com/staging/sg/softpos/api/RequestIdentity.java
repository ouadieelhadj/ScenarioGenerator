package com.staging.sg.softpos.api;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
public class RequestIdentity {
    private final boolean laboratoryHeaders;
    public RequestIdentity(@Value("${softpos.security.allow-laboratory-headers:false}") boolean laboratoryHeaders) {
        this.laboratoryHeaders = laboratoryHeaders;
    }
    public Identity resolve(Authentication authentication, String memberHeader, String deviceHeader) {
        if (authentication instanceof JwtAuthenticationToken jwt) {
            return new Identity(required(jwt.getToken().getClaimAsString("member_id"), "member_id"),
                    jwt.getToken().getClaimAsString("device_id"));
        }
        if (laboratoryHeaders) return new Identity(required(memberHeader, "X-Member-Id"), deviceHeader);
        throw new IllegalStateException("Authenticated member identity is required");
    }
    private static String required(String value, String name) { if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required"); return value; }
    public record Identity(String memberId, String deviceId) {}
}
