package com.staging.sg.switchlab.contracts;

public record SwitchLabMtipSentinelRequest(
        String pan,
        String expiry,
        String pin,
        String terminalId,
        String merchantId,
        String amount,
        Boolean macEnabled) {
}
