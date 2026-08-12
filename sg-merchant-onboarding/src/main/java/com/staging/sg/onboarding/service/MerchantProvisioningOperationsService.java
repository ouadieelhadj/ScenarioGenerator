package com.staging.sg.onboarding.service;

import com.staging.sg.onboarding.domain.*;
import com.staging.sg.onboarding.port.*;
import com.staging.sg.onboarding.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.nio.charset.StandardCharsets;

@Service
public class MerchantProvisioningOperationsService {
    private final MerchantOnboardingCaseRepository cases;
    private final OnboardingWay4ExportStateRepository way4States;
    private final MerchantProvisioningV2CommandFactory commands;
    private final Way4ConnectorPort way4;
    private final OnboardingOutboxEventRepository outboxEvents;
    private final OnboardingOutboxAdministrationService outboxAdministration;

    public MerchantProvisioningOperationsService(MerchantOnboardingCaseRepository cases,
            OnboardingWay4ExportStateRepository way4States,
            MerchantProvisioningV2CommandFactory commands, Way4ConnectorPort way4,
            OnboardingOutboxEventRepository outboxEvents,
            OnboardingOutboxAdministrationService outboxAdministration) {
        this.cases=cases; this.way4States=way4States; this.commands=commands;
        this.way4=way4; this.outboxEvents=outboxEvents;
        this.outboxAdministration=outboxAdministration;
    }

    @Transactional(readOnly=true)
    public List<Way4Candidate> way4Candidates() {
        return way4States.findByStatusInOrderByUpdatedAtAsc(List.of("PENDING","REJECTED")).stream()
                .map(state -> candidate(state, dossier(state.caseId()))).toList();
    }

    public Way4BatchResult generateWay4Batch(List<UUID> caseIds, String correlationId) {
        if(caseIds==null || caseIds.isEmpty() || caseIds.size()>500)
            throw new IllegalArgumentException("Select between 1 and 500 WAY4 merchants");
        List<UUID> unique=caseIds.stream().distinct().toList();
        if(unique.size()!=caseIds.size()) throw new IllegalArgumentException("Duplicate WAY4 merchant selection");
        String canonicalSelection=unique.stream().sorted().map(UUID::toString)
                .reduce((left,right)->left+":"+right).orElseThrow();
        String idempotencyKey="portal-way4-batch:"+UUID.nameUUIDFromBytes(
                canonicalSelection.getBytes(StandardCharsets.UTF_8));
        List<OnboardingWay4ExportState> states=unique.stream().map(id->way4States.findById(id)
                .orElseThrow(()->new IllegalArgumentException("WAY4 candidate not found: "+id))).toList();
        for(var state:states) if(!List.of("PENDING","REJECTED").contains(state.status()))
            throw new IllegalStateException("WAY4 candidate is no longer available: "+state.caseId());
        List<PortalWay4ExportCommand> payload=new ArrayList<>();
        for(var state:states){
            MerchantOnboardingCase dossier=dossier(state.caseId());
            if(dossier.provisioningDestination()==null || !dossier.provisioningDestination().includesWay4())
                throw new IllegalStateException("Dossier is not routed to WAY4: "+dossier.id());
            MerchantProvisioningCommandV2 command=commands.create(dossier);
            payload.add(new PortalWay4ExportCommand("2.0",dossier.id(),state.applicationRegNumber(),
                    dossier.productId(),command.merchant(),command.settlement(),command.outlets(),
                    "merchant-way4-v2:"+dossier.id()));
        }
        try {
            Way4ConnectorPort.Result result=way4.generateBatch(payload,idempotencyKey,correlationId);
            states.forEach(state->{state.generated(result.fileId());way4States.save(state);});
            return new Way4BatchResult(result.fileId(),result.fileName(),states.size(),result.status(),
                    result.xmlSha256(),result.xsdSha256(),result.xml());
        } catch(RuntimeException exception) {
            states.forEach(state->{state.failed("WAY4_BATCH_GENERATION",exception.getMessage(),false);way4States.save(state);});
            throw exception;
        }
    }

    @Transactional(readOnly=true)
    public List<FuturPaymentCandidate> futurPaymentCandidates() {
        return outboxEvents.findByEventTypeAndStatusInOrderByUpdatedAtAsc(
                "merchant.provisioning.requested",List.of(OnboardingOutboxStatus.PENDING,
                        OnboardingOutboxStatus.PROCESSING,OnboardingOutboxStatus.FAILED_FINAL)).stream()
                .map(event->{MerchantOnboardingCase value=dossier(event.aggregateId());
                    return new FuturPaymentCandidate(event.id(),value.id(),value.reference(),value.legalName(),
                            value.registrationNumber(),event.status().name(),event.attempts(),
                            event.lastErrorCode(),event.lastErrorMessage());}).toList();
    }

    public OnboardingOutboxEvent resendFuturPayment(UUID eventId,String actor,String reason) {
        return outboxAdministration.retry(eventId,actor,reason);
    }

    private Way4Candidate candidate(OnboardingWay4ExportState state,MerchantOnboardingCase dossier){
        return new Way4Candidate(dossier.id(),dossier.reference(),dossier.legalName(),
                dossier.registrationNumber(),state.applicationRegNumber(),state.status(),
                state.lastErrorCode(),state.lastErrorMessage());
    }
    private MerchantOnboardingCase dossier(UUID id){return cases.findById(id)
            .orElseThrow(()->new IllegalArgumentException("Onboarding case not found: "+id));}
    public record Way4Candidate(UUID caseId,String reference,String legalName,String registrationNumber,
            String applicationRegNumber,String status,String lastErrorCode,String lastErrorMessage){}
    public record Way4BatchResult(UUID fileId,String fileName,int merchantCount,String status,
            String xmlSha256,String xsdSha256,String xml){}
    public record FuturPaymentCandidate(UUID eventId,UUID caseId,String reference,String legalName,
            String registrationNumber,String status,int attempts,String lastErrorCode,String lastErrorMessage){}
}
