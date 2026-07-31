package com.staging.sg.card.issuing.service;

import com.staging.sg.card.issuing.domain.AuthorizationEvent;
import com.staging.sg.card.issuing.domain.IssuingAuthorization;
import com.staging.sg.card.issuing.repository.AuthorizationEventRepository;
import com.staging.sg.card.issuing.repository.IssuingAuthorizationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class AuthorizationJournalService {
    private final IssuingAuthorizationRepository authorizations;
    private final AuthorizationEventRepository events;
    public AuthorizationJournalService(IssuingAuthorizationRepository authorizations,
                                       AuthorizationEventRepository events){
        this.authorizations=authorizations; this.events=events;
    }
    @Transactional(readOnly=true)
    public Optional<IssuingAuthorization> replay(
            String issuerId,String callerId,String idempotencyKey,String fingerprint){
        var existing=authorizations.findByIssuerIdAndCallerIdAndIdempotencyKey(
                issuerId,callerId,idempotencyKey);
        if(existing.isPresent()&&!existing.get().requestMatches(fingerprint))
            throw new IllegalStateException("Idempotency key already used with another authorization payload");
        return existing;
    }
    @Transactional
    public IssuingAuthorization record(IssuingAuthorization authorization,String correlationId){
        authorizations.save(authorization);
        events.save(AuthorizationEvent.recorded(
                authorization.id(),"AuthorizationDecided",correlationId,
                "{\"status\":\""+authorization.status()
                        +"\",\"responseCode\":\""+safe(authorization.internalResponseCode())+"\"}"));
        return authorization;
    }
    private static String safe(String value){
        return value.replace("\\","\\\\").replace("\"","\\\"");
    }
}
