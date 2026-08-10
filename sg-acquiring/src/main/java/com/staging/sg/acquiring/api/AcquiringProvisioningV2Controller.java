package com.staging.sg.acquiring.api;

import com.staging.sg.acquiring.service.MerchantProvisioningV2Service;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/internal/acquiring/v2/merchant-provisioning")
public class AcquiringProvisioningV2Controller {
    private final MerchantProvisioningV2Service service;

    public AcquiringProvisioningV2Controller(MerchantProvisioningV2Service service) {
        this.service = service;
    }

    @PostMapping
    public MerchantProvisioningResultV2 provision(@RequestBody MerchantProvisioningRequestV2 request,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader("X-Correlation-ID") String correlationId) {
        return service.provision(request, idempotencyKey, correlationId);
    }
}
