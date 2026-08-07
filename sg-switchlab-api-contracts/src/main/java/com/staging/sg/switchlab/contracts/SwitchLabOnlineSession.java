package com.staging.sg.switchlab.contracts;

import java.time.Instant;

public record SwitchLabOnlineSession(String networkCode, String status, String role, String mode,
                                     String interfaceCode, String bankCode, boolean connected,
                                     Instant observedAt) { }
