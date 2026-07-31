package com.staging.sg.card.issuing.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.staging.sg.card.issuing.api.CreateIssuingInterfaceRequest;
import com.staging.sg.card.issuing.domain.IssuingInterfaceDirection;
import com.staging.sg.card.issuing.domain.IssuingInterfaceEndpoint;
import com.staging.sg.card.issuing.domain.IssuingInterfaceProtocol;
import com.staging.sg.card.issuing.domain.IssuingInterfaceStatus;
import com.staging.sg.card.issuing.domain.IssuingInterfaceType;
import com.staging.sg.card.issuing.repository.IssuingInterfaceEndpointRepository;
import com.staging.sg.card.issuing.repository.OutboxEventRepository;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IssuingInterfaceServiceTest {
    @Test
    void idempotentReplayReturnsStoredConfiguration() {
        IssuingInterfaceEndpointRepository endpoints =
                mock(IssuingInterfaceEndpointRepository.class);
        OutboxEventRepository outbox = mock(OutboxEventRepository.class);
        CreateIssuingInterfaceRequest request = request(1, Map.of("poolSize", "4"));
        IssuingInterfaceService service =
                new IssuingInterfaceService(endpoints, outbox, new ObjectMapper());

        var first = service.create(request, "maker-1", "idem-1", "corr-1");
        // The service owns the fingerprint; return the object captured on first save.
        var captor = org.mockito.ArgumentCaptor.forClass(
                IssuingInterfaceEndpoint.class);
        verify(endpoints).save(captor.capture());
        IssuingInterfaceEndpoint stored = captor.getValue();
        when(endpoints.findByIssuerIdAndCreatedByAndCreationIdempotencyKey(
                "ISSUER-1", "maker-1", "idem-1"))
                .thenReturn(Optional.of(stored));

        var replay = service.create(
                request, "maker-1", "idem-1", "corr-2");

        assertEquals(first.id(), replay.id());
        assertTrue(replay.replayed());
    }

    @Test
    void activatingNewVersionDisablesPreviousActiveVersion() {
        IssuingInterfaceEndpointRepository endpoints =
                mock(IssuingInterfaceEndpointRepository.class);
        OutboxEventRepository outbox = mock(OutboxEventRepository.class);
        IssuingInterfaceService service =
                new IssuingInterfaceService(endpoints, outbox, new ObjectMapper());
        IssuingInterfaceEndpoint previous = endpoint(1);
        previous.approve("checker-1");
        previous.activate();
        IssuingInterfaceEndpoint next = endpoint(2);
        next.approve("checker-1");
        when(endpoints.findById(next.id())).thenReturn(Optional.of(next));
        when(endpoints
                .findFirstByIssuerIdAndInterfaceTypeAndStatusOrderByInterfaceVersionDesc(
                        "ISSUER-1", IssuingInterfaceType.HSM,
                        IssuingInterfaceStatus.ACTIVE))
                .thenReturn(Optional.of(previous));

        service.activate(next.id(), "ISSUER-1", "corr-1");

        assertEquals(IssuingInterfaceStatus.DISABLED, previous.status());
        assertEquals(IssuingInterfaceStatus.ACTIVE, next.status());
        verify(endpoints).saveAndFlush(previous);
    }

    @Test
    void rejectsSecretsInsideGenericParameters() {
        IssuingInterfaceEndpointRepository endpoints =
                mock(IssuingInterfaceEndpointRepository.class);
        OutboxEventRepository outbox = mock(OutboxEventRepository.class);
        IssuingInterfaceService service =
                new IssuingInterfaceService(endpoints, outbox, new ObjectMapper());

        assertThrows(IllegalArgumentException.class, () -> service.create(
                request(1, Map.of("apiToken", "must-not-be-stored")),
                "maker-1", "idem-1", "corr-1"));

        verify(endpoints, never()).save(any());
    }

    @Test
    void resolverFailsClosedWithoutActiveDatabaseConfiguration() {
        IssuingInterfaceEndpointRepository endpoints =
                mock(IssuingInterfaceEndpointRepository.class);
        IssuingEndpointResolver resolver = new IssuingEndpointResolver(endpoints);
        when(endpoints
                .findFirstByIssuerIdAndInterfaceTypeAndStatusOrderByInterfaceVersionDesc(
                        "ISSUER-1", IssuingInterfaceType.CORE_BANKING,
                        IssuingInterfaceStatus.ACTIVE))
                .thenReturn(Optional.empty());

        assertThrows(IllegalStateException.class, () ->
                resolver.requireActive(
                        "ISSUER-1", IssuingInterfaceType.CORE_BANKING));
    }

    private static CreateIssuingInterfaceRequest request(
            int version, Map<String, String> parameters) {
        return new CreateIssuingInterfaceRequest(
                "ISSUER-1", IssuingInterfaceType.HSM, version,
                IssuingInterfaceDirection.OUTBOUND,
                IssuingInterfaceProtocol.TLS_TCP, "hsm.internal", 1500,
                null, 500, 1000, "issuer-hsm-tls",
                "vault://issuing/hsm", parameters);
    }

    private static IssuingInterfaceEndpoint endpoint(int version) {
        return IssuingInterfaceEndpoint.draft(
                "ISSUER-1", IssuingInterfaceType.HSM, version,
                IssuingInterfaceDirection.OUTBOUND,
                IssuingInterfaceProtocol.TLS_TCP, "hsm.internal", 1500,
                null, 500, 1000, "issuer-hsm-tls",
                "vault://issuing/hsm", "{}", "maker-1",
                "idem-" + version, "fingerprint-" + version);
    }
}
