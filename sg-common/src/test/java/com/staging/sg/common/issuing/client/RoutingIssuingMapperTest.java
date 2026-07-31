package com.staging.sg.common.issuing.client;

import com.staging.sg.common.issuing.IssuingOperation;
import com.staging.sg.common.routing.RoutingTransactionRequest;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RoutingIssuingMapperTest {
    @Test
    void mapsPanWithoutExposingItInRequestRepresentation() {
        var source = new RoutingTransactionRequest(
                "1.0", "txn-1", "corr-1", "idem-1", "HOLD",
                "0100", "000000", "5321960000003348", "2912",
                "000000001000", "504", "000001", "123456789012",
                "TERM-1", "MERCHANT-1", null, null, null,
                Map.of("issuerId", "ISSUER-1"));

        var mapped = RoutingIssuingMapper.request(source, "WAY_POS_SERVER");

        assertEquals(IssuingOperation.AUTHORIZATION, mapped.operation());
        assertEquals(1000, mapped.amountMinor());
        assertFalse(mapped.toString().contains("5321960000003348"));
    }
}
