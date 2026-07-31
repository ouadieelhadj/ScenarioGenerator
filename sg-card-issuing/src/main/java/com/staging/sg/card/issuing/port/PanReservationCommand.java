package com.staging.sg.card.issuing.port;

import java.util.UUID;

public record PanReservationCommand(
        String issuerId,
        UUID contractId,
        UUID productId,
        String correlationId,
        String idempotencyKey) {
}
