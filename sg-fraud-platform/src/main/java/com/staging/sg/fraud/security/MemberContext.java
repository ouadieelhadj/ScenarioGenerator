package com.staging.sg.fraud.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
public class MemberContext {
    private final String claim;
    public MemberContext(@Value("${fraud.security.member-claim:member_id}") String claim) { this.claim = claim; }
    public String requireMemberId(Authentication authentication) {
        if (!(authentication instanceof JwtAuthenticationToken jwt)) throw new IllegalStateException("Authenticated member required");
        String memberId = jwt.getToken().getClaimAsString(claim);
        if (memberId == null || !memberId.matches("[A-Za-z0-9_-]{2,64}")) throw new IllegalStateException("Valid member claim required");
        return memberId;
    }
}
