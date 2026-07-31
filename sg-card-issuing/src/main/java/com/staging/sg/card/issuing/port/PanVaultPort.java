package com.staging.sg.card.issuing.port;

public interface PanVaultPort {
    ProtectedPan reserveVirtualPan(PanReservationCommand command);
}
