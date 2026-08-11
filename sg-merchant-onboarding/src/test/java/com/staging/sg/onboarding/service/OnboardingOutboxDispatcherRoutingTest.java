package com.staging.sg.onboarding.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.staging.sg.onboarding.port.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OnboardingOutboxDispatcherRoutingTest {
    @Test void routesAcquiringAndWay4Independently(){
        var reservations=mock(OnboardingOutboxReservationService.class);
        var completions=mock(OnboardingOutboxCompletionService.class);
        var acquiring=mock(AcquiringProvisioningV2Port.class);var way4=mock(Way4ConnectorPort.class);
        UUID caseId=UUID.randomUUID();UUID acqEvent=UUID.randomUUID();UUID way4Event=UUID.randomUUID();
        String acqJson="{\"schemaVersion\":\"2.0\",\"onboardingCaseId\":\""+caseId+"\"}";
        String way4Json="{\"schemaVersion\":\"2.0\",\"onboardingCaseId\":\""+caseId+"\",\"applicationRegNumber\":\"PORTAL-TEST\",\"idempotencyKey\":\"merchant-way4-v2:"+caseId+"\"}";
        when(reservations.reserve(20,true)).thenReturn(List.of(
                new OnboardingOutboxReservationService.ReservedEvent(acqEvent,caseId,"merchant.provisioning.requested","merchant-onboarding-v2:"+caseId,acqJson,"corr-a"),
                new OnboardingOutboxReservationService.ReservedEvent(way4Event,caseId,"way4.export.requested","merchant-way4-v2:"+caseId,way4Json,"corr-w")));
        var acqResult=new MerchantProvisioningResultV2("2.0",UUID.randomUUID(),"MID","PROVISIONED",List.of());
        var way4Result=new Way4ConnectorPort.Result(UUID.randomUUID(),"VALIDATED");
        when(acquiring.provision(any(),anyString(),anyString())).thenReturn(acqResult);
        when(way4.generate(any(),anyString())).thenReturn(way4Result);
        new OnboardingOutboxDispatcher(true,20,true,reservations,completions,acquiring,way4,new ObjectMapper()).dispatch();
        verify(acquiring).provision(any(),eq("merchant-onboarding-v2:"+caseId),eq("corr-a"));
        verify(way4).generate(any(),eq("corr-w"));
        verify(completions).result(acqEvent,acqResult);verify(completions).way4Result(way4Event,way4Result);
    }

    @Test void keepsWay4EventsUnreservedWhenConnectorIsDisabled(){
        var reservations=mock(OnboardingOutboxReservationService.class);
        var completions=mock(OnboardingOutboxCompletionService.class);
        var acquiring=mock(AcquiringProvisioningV2Port.class);var way4=mock(Way4ConnectorPort.class);
        when(reservations.reserve(20,false)).thenReturn(List.of());
        new OnboardingOutboxDispatcher(true,20,false,reservations,completions,acquiring,way4,new ObjectMapper()).dispatch();
        verify(reservations).holdWay4();
        verify(reservations).reserve(20,false);
        verifyNoInteractions(completions,acquiring,way4);
    }
}
