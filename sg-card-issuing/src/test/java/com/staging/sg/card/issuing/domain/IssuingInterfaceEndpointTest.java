package com.staging.sg.card.issuing.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IssuingInterfaceEndpointTest {
    @Test
    void requiresMakerCheckerBeforeActivation() {
        IssuingInterfaceEndpoint endpoint = endpoint(1, "maker-1");

        assertThrows(IllegalStateException.class, endpoint::activate);
        assertThrows(IllegalStateException.class,
                () -> endpoint.approve("maker-1"));

        endpoint.approve("checker-1");
        endpoint.activate();

        assertEquals(IssuingInterfaceStatus.ACTIVE, endpoint.status());
    }

    @Test
    void validatesPortAndTlsProfile() {
        assertThrows(IllegalArgumentException.class, () ->
                IssuingInterfaceEndpoint.draft(
                        "ISSUER-1", IssuingInterfaceType.HSM, 1,
                        IssuingInterfaceDirection.OUTBOUND,
                        IssuingInterfaceProtocol.TLS_TCP,
                        "hsm.internal", 70000, null, 500, 1000,
                        null, "vault://issuing/hsm", "{}",
                        "maker-1", "idem-1", "fingerprint"));
        assertThrows(IllegalArgumentException.class, () ->
                IssuingInterfaceEndpoint.draft(
                        "ISSUER-1", IssuingInterfaceType.HSM, 1,
                        IssuingInterfaceDirection.OUTBOUND,
                        IssuingInterfaceProtocol.TLS_TCP,
                        "hsm.internal", 1500, null, 500, 1000,
                        null, "vault://issuing/hsm", "{}",
                        "maker-1", "idem-1", "fingerprint"));
    }

    private static IssuingInterfaceEndpoint endpoint(
            int version, String maker) {
        return IssuingInterfaceEndpoint.draft(
                "ISSUER-1", IssuingInterfaceType.HSM, version,
                IssuingInterfaceDirection.OUTBOUND,
                IssuingInterfaceProtocol.TLS_TCP,
                "hsm.internal", 1500, null, 500, 1000,
                "issuer-hsm-tls", "vault://issuing/hsm", "{}",
                maker, "idem-" + version, "fingerprint-" + version);
    }
}
