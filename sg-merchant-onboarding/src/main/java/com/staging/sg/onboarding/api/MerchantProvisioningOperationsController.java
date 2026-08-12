package com.staging.sg.onboarding.api;

import com.staging.sg.onboarding.service.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import java.util.*;

@RestController
@RequestMapping("/api/merchant-onboarding/v2/operations")
@PreAuthorize("hasAnyRole('ADMIN','BACK_OFFICE') or hasAuthority('ONBOARDING_PROVISION')")
public class MerchantProvisioningOperationsController {
    private final MerchantProvisioningOperationsService service;
    public MerchantProvisioningOperationsController(MerchantProvisioningOperationsService service){this.service=service;}

    @GetMapping("/way4/candidates")
    public List<MerchantProvisioningOperationsService.Way4Candidate> way4Candidates(){return service.way4Candidates();}

    @PostMapping("/way4/batches")
    public MerchantProvisioningOperationsService.Way4BatchResult generate(@Valid @RequestBody Way4BatchRequest request,
            @RequestHeader("X-Correlation-ID") String correlationId){
        return service.generateWay4Batch(request.caseIds(),correlationId);
    }

    @GetMapping("/futurpayment/candidates")
    public List<MerchantProvisioningOperationsService.FuturPaymentCandidate> futurPaymentCandidates(){
        return service.futurPaymentCandidates();
    }

    @PostMapping("/futurpayment/{eventId}/resend")
    public FuturPaymentRetryView resend(@PathVariable UUID eventId,Authentication authentication){
        var event=service.resendFuturPayment(eventId,authentication.getName(),
                "Relance manuelle depuis l'ecran de provisionnement Merchant Portal");
        return new FuturPaymentRetryView(event.id(),event.status().name(),event.attempts());
    }
    public record Way4BatchRequest(@NotEmpty @Size(max=500) List<@NotNull UUID> caseIds){}
    public record FuturPaymentRetryView(UUID eventId,String status,int attempts){}
}
