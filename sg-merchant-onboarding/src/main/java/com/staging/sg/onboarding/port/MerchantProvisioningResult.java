package com.staging.sg.onboarding.port;

import java.util.List;
import java.util.UUID;

public record MerchantProvisioningResult(
        UUID merchantId,
        String merchantAcceptorId,
        List<TerminalResult> terminals) {
    public record TerminalResult(UUID terminalDeviceId, String terminalId) {}
}
