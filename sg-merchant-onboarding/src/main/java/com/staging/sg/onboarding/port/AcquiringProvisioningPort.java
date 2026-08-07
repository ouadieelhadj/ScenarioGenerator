package com.staging.sg.onboarding.port;

public interface AcquiringProvisioningPort {
    MerchantProvisioningResult provision(MerchantProvisioningCommand command,
            String idempotencyKey, String correlationId);
}
