package com.staging.sg.acquiring.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.staging.sg.acquiring.api.AcquiringOnboardingController.*;
import com.staging.sg.acquiring.domain.*;
import com.staging.sg.acquiring.repository.OnboardingProvisioningReceiptRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MerchantOnboardingProvisioningServiceTest {
    @Mock private AcquiringAdministrationService administration;
    @Mock private AcquiringIdentifierAllocator identifiers;
    @Mock private OnboardingProvisioningReceiptRepository receipts;
    private MerchantOnboardingProvisioningService service;

    @BeforeEach
    void setUp() {
        service = new MerchantOnboardingProvisioningService(administration, identifiers,
                receipts, new ObjectMapper());
    }

    @Test
    void acquiringOwnsMidTidAllocationAndReplaysCompletedRequest() {
        OnboardingProvisioningRequest request = request();
        String key = "merchant-onboarding:" + request.onboardingCaseId();
        Merchant merchant = activeMerchant();
        MerchantOutlet outlet = MerchantOutlet.active(merchant.id(), "OUT-01", "Main", "Rabat", "MA");
        AcquiringContract merchantContract = merchantContract(merchant.id());
        AcquiringContract firstDevice = deviceContract(merchant.id(), merchantContract.id(), 1);
        AcquiringContract secondDevice = deviceContract(merchant.id(), merchantContract.id(), 2);

        when(receipts.findById(key)).thenReturn(Optional.empty());
        when(identifiers.nextMid()).thenReturn("123456789012345");
        when(identifiers.nextTid()).thenReturn("10000001", "10000002");
        when(administration.createMerchant(anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString(), anyString())).thenReturn(merchant);
        when(administration.createOutlet(eq(merchant.id()), anyString(), anyString(), anyString(),
                anyString(), anyString())).thenReturn(outlet);
        when(administration.createMerchantContract(anyString(), anyString(), eq(merchant.id()),
                anyString(), any(), anyString(), anyString(), anyString(), any(),
                anyString(), anyString(), anyString())).thenReturn(merchantContract);
        when(administration.createDeviceContract(anyString(), anyString(), eq(merchant.id()),
                eq(merchantContract.id()), any(), eq(outlet.id()), anyString(), any(),
                anyBoolean(), anyString(), anyBoolean(), anyString(), anyString(), anyString()))
                .thenReturn(firstDevice, secondDevice);
        when(receipts.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        OnboardingProvisioningResult first = service.provision(request, key, "corr-1");

        assertThat(first.merchantId()).isEqualTo(merchant.id());
        assertThat(first.merchantAcceptorId()).isEqualTo("123456789012345");
        assertThat(first.terminals()).extracting(TerminalResult::terminalId)
                .containsExactly("10000001", "10000002");
        assertThat(first.terminals()).extracting(TerminalResult::terminalDeviceId)
                .containsOnlyNulls();
        verify(identifiers).nextMid();
        verify(identifiers, times(2)).nextTid();

        ArgumentCaptor<OnboardingProvisioningReceipt> receiptCaptor =
                ArgumentCaptor.forClass(OnboardingProvisioningReceipt.class);
        verify(receipts).save(receiptCaptor.capture());
        when(receipts.findById(key)).thenReturn(Optional.of(receiptCaptor.getValue()));
        clearInvocations(administration, identifiers);

        OnboardingProvisioningResult replay = service.provision(request, key, "corr-2");

        assertThat(replay).isEqualTo(first);
        verifyNoInteractions(administration, identifiers);
    }

    private static OnboardingProvisioningRequest request() {
        return new OnboardingProvisioningRequest(UUID.randomUUID(), "ONB-12345678", "ACQ-01",
                "Merchant Legal", "Merchant Shop", "RC-123", "MA", "5411",
                "ACC-001", "504", UUID.randomUUID(), "BOTH",
                new Outlet("OUT-01", "Main", "Rabat", 2), "merchant.user", "checker.user");
    }

    private static Merchant activeMerchant() {
        Merchant merchant = Merchant.draft("ACQ-01", "Merchant Legal", "Merchant Shop",
                "RC-123", "MA", "5411", "merchant.user", "key", "0".repeat(64));
        merchant.submit();
        merchant.approve("checker.user");
        return merchant;
    }

    private static AcquiringContract merchantContract(UUID merchantId) {
        return AcquiringContract.merchant("ACQ-01", "ONB:MERCHANT", merchantId, "ACC-001",
                UUID.randomUUID(), "merchant.user", "contract-key", "1".repeat(64));
    }

    private static AcquiringContract deviceContract(UUID merchantId, UUID parentId, int index) {
        return AcquiringContract.device("ACQ-01", "ONB:TPE:" + index, merchantId, parentId,
                UUID.randomUUID(), "merchant.user", "device-key-" + index, "2".repeat(64));
    }
}
