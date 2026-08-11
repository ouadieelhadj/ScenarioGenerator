package com.staging.sg.onboarding.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.staging.sg.onboarding.domain.*;
import com.staging.sg.onboarding.port.MerchantProvisioningCommandV2;
import com.staging.sg.onboarding.repository.OnboardingOutboxEventRepository;
import com.staging.sg.onboarding.repository.OnboardingWay4ExportStateRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class OnboardingDestinationRoutingTest {

    @Test void futurPaymentCreatesOnlyAcquiringEvent(){assertRouting(ProvisioningDestination.FUTURPAYMENT,
            List.of("merchant.provisioning.requested"),false);}
    @Test void way4CreatesOnlyWay4Event(){assertRouting(ProvisioningDestination.WAY4,
            List.of("way4.export.requested"),true);}
    @Test void bothCreatesBothIndependentEvents(){assertRouting(ProvisioningDestination.BOTH,
            List.of("merchant.provisioning.requested","way4.export.requested"),true);}

    private void assertRouting(ProvisioningDestination destination,List<String> expectedTypes,
            boolean expectsWay4State){
        var events=mock(OnboardingOutboxEventRepository.class);
        var commands=mock(MerchantProvisioningV2CommandFactory.class);
        var states=mock(OnboardingWay4ExportStateRepository.class);
        var dossier=mock(MerchantOnboardingCase.class);
        UUID caseId=UUID.randomUUID();
        when(dossier.id()).thenReturn(caseId);when(dossier.merchantType()).thenReturn(MerchantType.PM);
        when(dossier.provisioningDestination()).thenReturn(destination);when(dossier.productId()).thenReturn(UUID.randomUUID());
        when(events.findByIdempotencyKey(any())).thenReturn(Optional.empty());
        when(states.findById(caseId)).thenReturn(Optional.empty());
        when(events.save(any())).thenAnswer(invocation->invocation.getArgument(0));
        when(states.save(any())).thenAnswer(invocation->invocation.getArgument(0));
        var merchant=new MerchantProvisioningCommandV2.LegalMerchant("PM",null,"Legal","Shop","RC",null,
                "ICE",null,"Retail",null,"+212600000000","merchant@example.test","RIB",
                null,null,List.of(),"5411");
        var command=new MerchantProvisioningCommandV2("2.0",caseId,"ONB","ACQ",merchant,
                new MerchantProvisioningCommandV2.Settlement("ACC","504"),"TPE",List.of(),"maker","checker");
        when(commands.create(dossier)).thenReturn(command);

        new OnboardingOutboxService(events,commands,new ObjectMapper(),states).enqueueApproved(dossier);

        ArgumentCaptor<OnboardingOutboxEvent> captor=ArgumentCaptor.forClass(OnboardingOutboxEvent.class);
        verify(events,times(expectedTypes.size())).save(captor.capture());
        assertThat(captor.getAllValues()).extracting(OnboardingOutboxEvent::eventType)
                .containsExactlyElementsOf(expectedTypes);
        if(expectsWay4State)verify(states).save(any());else verify(states,never()).save(any());
    }
}
