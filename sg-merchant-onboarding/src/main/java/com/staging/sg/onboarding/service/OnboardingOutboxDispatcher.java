package com.staging.sg.onboarding.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.staging.sg.onboarding.port.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OnboardingOutboxDispatcher {
    private final boolean enabled;
    private final int batchSize;
    private final boolean way4Enabled;
    private final OnboardingOutboxReservationService reservations;
    private final OnboardingOutboxCompletionService completions;
    private final AcquiringProvisioningV2Port acquiring;
    private final Way4ConnectorPort way4;
    private final ObjectMapper objectMapper;

    public OnboardingOutboxDispatcher(
            @Value("${merchant-onboarding.outbox.enabled:false}") boolean enabled,
            @Value("${merchant-onboarding.outbox.batch-size:20}") int batchSize,
            @Value("${merchant-onboarding.way4-connector.enabled:false}") boolean way4Enabled,
            OnboardingOutboxReservationService reservations,
            OnboardingOutboxCompletionService completions,
            AcquiringProvisioningV2Port acquiring, Way4ConnectorPort way4, ObjectMapper objectMapper) {
        this.enabled = enabled;
        this.batchSize = batchSize;
        this.way4Enabled = way4Enabled;
        this.reservations = reservations;
        this.completions = completions;
        this.acquiring = acquiring;
        this.way4 = way4;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelayString = "${merchant-onboarding.outbox.poll-delay-ms:5000}")
    public void dispatch() {
        if (!enabled) return;
        // WAY4 is now operator-triggered as a multi-merchant file. Historical automatic
        // events remain pending and are deliberately never reserved by this dispatcher.
        reservations.holdWay4();
        for (OnboardingOutboxReservationService.ReservedEvent event : reservations.reserve(batchSize, false))
            dispatch(event);
    }

    private void dispatch(OnboardingOutboxReservationService.ReservedEvent event) {
        if("way4.export.requested".equals(event.eventType())) { dispatchWay4(event); return; }
        if(!"merchant.provisioning.requested".equals(event.eventType())) {
            completions.failure(event.eventId(),"UNKNOWN_OUTBOX_EVENT","Unsupported outbox event type",false); return;
        }
        try {
            MerchantProvisioningCommandV2 command = objectMapper.readValue(
                    event.payloadJson(), MerchantProvisioningCommandV2.class);
            completions.result(event.eventId(), acquiring.provision(command,
                    event.idempotencyKey(), event.correlationId()));
        } catch (ProvisioningTransportException exception) {
            completions.failure(event.eventId(), "ACQUIRING_TRANSPORT",
                    exception.getMessage(), exception.retryable());
        } catch (JsonProcessingException exception) {
            completions.failure(event.eventId(), "INVALID_OUTBOX_PAYLOAD",
                    "Stored provisioning event cannot be deserialized", false);
        } catch (RuntimeException exception) {
            completions.failure(event.eventId(), "UNEXPECTED_DISPATCH_FAILURE",
                    exception.getClass().getSimpleName(), true);
        }
    }

    private void dispatchWay4(OnboardingOutboxReservationService.ReservedEvent event) {
        try {
            PortalWay4ExportCommand command=objectMapper.readValue(event.payloadJson(),PortalWay4ExportCommand.class);
            completions.way4Result(event.eventId(),way4.generate(command,event.correlationId()));
        } catch(Way4ConnectorTransportException e){completions.failure(event.eventId(),"WAY4_CONNECTOR",e.getMessage(),e.retryable());}
        catch(JsonProcessingException e){completions.failure(event.eventId(),"INVALID_WAY4_PAYLOAD","Stored WAY4 payload cannot be deserialized",false);}
        catch(RuntimeException e){completions.failure(event.eventId(),"UNEXPECTED_WAY4_FAILURE",e.getClass().getSimpleName(),true);}
    }
}
