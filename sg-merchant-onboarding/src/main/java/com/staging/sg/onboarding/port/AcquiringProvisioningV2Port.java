package com.staging.sg.onboarding.port;

public interface AcquiringProvisioningV2Port {
    MerchantProvisioningResultV2 provision(MerchantProvisioningCommandV2 command,
            String idempotencyKey, String correlationId);
}
