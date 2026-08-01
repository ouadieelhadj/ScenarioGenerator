package com.staging.sg.mc.dmas.mastercard.network;

import com.staging.sg.common.issuing.IssuingAuthorizationResponse;
import com.staging.sg.common.issuing.IssuingDecisionStatus;
import com.staging.sg.common.issuing.client.DatabaseIssuingClient;
import com.staging.sg.common.service.McDmasInterfaceService;
import org.jpos.iso.ISOMsg;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentCaptor;
import com.staging.sg.common.issuing.IssuingAuthorizationRequest;

class DmasIssuingAdapterTest {
    @Test
    void delegatesToIssuingAndMapsInsufficientFunds() throws Exception {
        DatabaseIssuingClient issuing = mock(DatabaseIssuingClient.class);
        McDmasInterfaceService interfaces =
                mock(McDmasInterfaceService.class);
        when(interfaces.bankCode()).thenReturn("BANK1");
        when(issuing.authorize(eq("DMAS"), any())).thenReturn(
                new IssuingAuthorizationResponse(
                        "1.0", "BANK1", "DMAS-0731100000-123456-REF1",
                        "DMAS-0731100000-123456-REF1",
                        IssuingDecisionStatus.DECLINED, "INSUFFICIENT_FUNDS",
                        null, 0, "504", null, false, Map.of()));

        DmasIssuingAdapter.Decision result =
                new DmasIssuingAdapter(issuing, interfaces)
                        .authorize(message());

        assertThat(result.responseCode()).isEqualTo("51");
        assertThat(result.status()).isEqualTo("DECLINED");
        assertThat(result.retryable()).isFalse();
    }

    @Test
    void marksManualCondition59AsEcommerce() throws Exception {
        DatabaseIssuingClient issuing = mock(DatabaseIssuingClient.class);
        McDmasInterfaceService interfaces = mock(McDmasInterfaceService.class);
        when(interfaces.bankCode()).thenReturn("BANK1");
        when(issuing.authorize(eq("DMAS"), any())).thenReturn(
                new IssuingAuthorizationResponse("1.0", "BANK1", "T", "C",
                        IssuingDecisionStatus.APPROVED, "APPROVED", "123456",
                        1000, "504", null, false, Map.of()));
        ISOMsg message = message();
        message.set(22, "010");
        message.set(25, "59");

        new DmasIssuingAdapter(issuing, interfaces).authorize(message);

        ArgumentCaptor<IssuingAuthorizationRequest> request =
                ArgumentCaptor.forClass(IssuingAuthorizationRequest.class);
        verify(issuing).authorize(eq("DMAS"), request.capture());
        assertThat(request.getValue().ecommerce()).isTrue();
        assertThat(request.getValue().cardPresent()).isFalse();
        assertThat(request.getValue().attributes())
                .containsEntry("authenticationStatus", "NOT_PERFORMED");
    }

    private static ISOMsg message() throws Exception {
        ISOMsg value = new ISOMsg("0100");
        value.set(2, "1234567890123456");
        value.set(3, "000000");
        value.set(4, "000000001000");
        value.set(7, "0731100000");
        value.set(11, "123456");
        value.set(12, "260731100000");
        value.set(18, "5411");
        value.set(19, "504");
        value.set(37, "REF1");
        value.set(41, "TERM1");
        value.set(42, "MERCHANT1");
        value.set(49, "504");
        return value;
    }
}
