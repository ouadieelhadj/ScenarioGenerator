package com.staging.sg.card.issuing.service;

import com.staging.sg.card.issuing.domain.IssuingAuthorization;
import com.staging.sg.card.issuing.repository.AuthorizationEventRepository;
import com.staging.sg.card.issuing.repository.IssuingAuthorizationRepository;
import com.staging.sg.common.issuing.IssuingDecisionStatus;
import com.staging.sg.common.issuing.IssuingOperation;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AuthorizationJournalServiceTest {
    @Test
    void recordsDecisionAndAppendOnlyEventAtomically() {
        var authorizations=mock(IssuingAuthorizationRepository.class);
        var events=mock(AuthorizationEventRepository.class);
        var service=new AuthorizationJournalService(authorizations,events);
        var decision=decision("fingerprint-1");

        service.record(decision,"corr-1");

        verify(authorizations).save(decision);
        verify(events).save(any());
    }

    @Test
    void rejectsIdempotencyKeyReuseWithDifferentRequest() {
        var authorizations=mock(IssuingAuthorizationRepository.class);
        var events=mock(AuthorizationEventRepository.class);
        when(authorizations.findByIssuerIdAndCallerIdAndIdempotencyKey(
                "ISSUER-1","POS","idem-1"))
                .thenReturn(Optional.of(decision("fingerprint-1")));
        var service=new AuthorizationJournalService(authorizations,events);

        assertThrows(IllegalStateException.class,()->service.replay(
                "ISSUER-1","POS","idem-1","fingerprint-2"));
    }

    @Test
    void identicalReplayReturnsOriginalDecision() {
        var authorizations=mock(IssuingAuthorizationRepository.class);
        var original=decision("fingerprint-1");
        when(authorizations.findByIssuerIdAndCallerIdAndIdempotencyKey(
                "ISSUER-1","POS","idem-1")).thenReturn(Optional.of(original));
        var service=new AuthorizationJournalService(
                authorizations,mock(AuthorizationEventRepository.class));

        assertSame(original,service.replay(
                "ISSUER-1","POS","idem-1","fingerprint-1").orElseThrow());
    }

    private static IssuingAuthorization decision(String fingerprint){
        return IssuingAuthorization.decided(
                "ISSUER-1","POS","txn-1","corr-1","idem-1",fingerprint,
                UUID.randomUUID(),IssuingOperation.AUTHORIZATION,null,
                1000,"504",IssuingDecisionStatus.DECLINED,
                "DO_NOT_HONOR",null,0,false);
    }
}
