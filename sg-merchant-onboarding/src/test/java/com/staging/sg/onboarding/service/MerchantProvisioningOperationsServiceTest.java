package com.staging.sg.onboarding.service;

import com.staging.sg.onboarding.domain.*;
import com.staging.sg.onboarding.port.*;
import com.staging.sg.onboarding.repository.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MerchantProvisioningOperationsServiceTest {
    @Test void sendsTwoSelectedMerchantsInOneWay4BatchAndMarksBothGenerated(){
        var cases=mock(MerchantOnboardingCaseRepository.class);var states=mock(OnboardingWay4ExportStateRepository.class);
        var factory=mock(MerchantProvisioningV2CommandFactory.class);var way4=mock(Way4ConnectorPort.class);
        var outbox=mock(OnboardingOutboxEventRepository.class);var administration=mock(OnboardingOutboxAdministrationService.class);
        UUID firstId=UUID.randomUUID(),secondId=UUID.randomUUID(),fileId=UUID.randomUUID(),productId=UUID.randomUUID();
        var firstState=state(firstId,"PORTAL-FIRST");var secondState=state(secondId,"PORTAL-SECOND");
        var first=dossier(firstId,productId);var second=dossier(secondId,productId);
        when(states.findById(firstId)).thenReturn(Optional.of(firstState));when(states.findById(secondId)).thenReturn(Optional.of(secondState));
        when(cases.findById(firstId)).thenReturn(Optional.of(first));when(cases.findById(secondId)).thenReturn(Optional.of(second));
        when(factory.create(any())).thenAnswer(invocation->command(invocation.getArgument(0),productId));
        when(way4.generateBatch(anyList(),startsWith("portal-way4-batch:"),eq("correlation"))).thenReturn(
                new Way4ConnectorPort.Result(fileId,"FP_WAY4_0000000001.xml","VALIDATED","xml","xsd","<xml/>"));
        var service=new MerchantProvisioningOperationsService(cases,states,factory,way4,outbox,administration);

        var result=service.generateWay4Batch(List.of(firstId,secondId),"correlation");

        ArgumentCaptor<List<PortalWay4ExportCommand>> payload=ArgumentCaptor.forClass(List.class);
        verify(way4).generateBatch(payload.capture(),startsWith("portal-way4-batch:"),eq("correlation"));
        assertEquals(2,payload.getValue().size());assertEquals(2,result.merchantCount());
        verify(firstState).generated(fileId);verify(secondState).generated(fileId);
    }

    private static OnboardingWay4ExportState state(UUID id,String reg){var value=mock(OnboardingWay4ExportState.class);
        when(value.caseId()).thenReturn(id);when(value.applicationRegNumber()).thenReturn(reg);when(value.status()).thenReturn("PENDING");return value;}
    private static MerchantOnboardingCase dossier(UUID id,UUID product){var value=mock(MerchantOnboardingCase.class);
        when(value.id()).thenReturn(id);when(value.productId()).thenReturn(product);when(value.provisioningDestination()).thenReturn(ProvisioningDestination.WAY4);return value;}
    private static MerchantProvisioningCommandV2 command(MerchantOnboardingCase dossier,UUID product){
        var merchant=new MerchantProvisioningCommandV2.LegalMerchant("PM",null,"Legal","Shop","RC",null,null,null,null,null,
                null,null,null,null,null,List.of(),"5411");
        return new MerchantProvisioningCommandV2("2.0",dossier.id(),"REF","ACQ",merchant,
                new MerchantProvisioningCommandV2.Settlement("ACCOUNT","504"),"TPE",List.of(),"maker","checker");
    }
}
