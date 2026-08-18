package com.staging.sg.softpos.service;

import com.staging.sg.softpos.contracts.SoftPosContracts.PosServerMode;
import com.staging.sg.softpos.contracts.SoftPosContracts.PosServerPaymentCommand;
import com.staging.sg.softpos.contracts.SoftPosContracts.PosServerPaymentResult;
import com.staging.sg.softpos.domain.SoftPosPosServerRoute;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import java.time.Duration;

@Component
public class RestJsonPosServerConnector implements PosServerConnector {
    private final RestClient.Builder clients;
    public RestJsonPosServerConnector(RestClient.Builder clients) { this.clients = clients; }
    @Override public PosServerMode mode() { return PosServerMode.REST_JSON; }
    @Override public PosServerPaymentResult exchange(PosServerPaymentCommand command, SoftPosPosServerRoute route) {
        PosServerPaymentResult result = clients.baseUrl(route.getEndpoint()).build().post()
                .uri("/api/internal/softpos/v1/transactions")
                .contentType(MediaType.APPLICATION_JSON).header("Idempotency-Key", command.posTransactionId())
                .body(command).retrieve().body(PosServerPaymentResult.class);
        if (result == null) throw new IllegalStateException("Empty POServer response");
        return result;
    }
}
