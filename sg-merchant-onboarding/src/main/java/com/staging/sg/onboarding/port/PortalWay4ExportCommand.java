package com.staging.sg.onboarding.port;

import java.util.List;
import java.util.UUID;

public record PortalWay4ExportCommand(String schemaVersion, UUID onboardingCaseId,
        String applicationRegNumber, UUID accountProductId,
        MerchantProvisioningCommandV2.LegalMerchant merchant,
        MerchantProvisioningCommandV2.Settlement settlement,
        List<MerchantProvisioningCommandV2.Outlet> outlets, String idempotencyKey) {}
