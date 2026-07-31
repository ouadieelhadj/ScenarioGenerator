package com.staging.sg.waypos.server.service;

import com.staging.sg.common.issuing.client.DatabaseIssuingClient;
import com.staging.sg.common.issuing.client.RoutingIssuingMapper;
import com.staging.sg.common.routing.RoutingTransactionRequest;
import com.staging.sg.common.routing.RoutingTransactionResponse;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class Issuing00000Connector {
    private static final String ROUTE = "00000";
    private final DatabaseIssuingClient issuing;

    public Issuing00000Connector(DatabaseIssuingClient issuing) {
        this.issuing = issuing;
    }

    public RoutingTransactionResponse process(
            RoutingTransactionRequest request) {
        try {
            return RoutingIssuingMapper.response(
                    issuing.authorize(
                            "SERVER_POS",
                            RoutingIssuingMapper.request(
                                    request, "WAY_POS_SERVER")),
                    ROUTE);
        } catch (RuntimeException failure) {
            return new RoutingTransactionResponse(
                    request.transactionId(), "UNKNOWN", "91",
                    "ISSUING_UNAVAILABLE", null, ROUTE,
                    null, null, true,
                    Map.of("failure",
                            failure.getClass().getSimpleName()));
        }
    }
}
