package com.staging.sg.switchlab.contracts;

import java.time.Instant;

public record SwitchLabOnlineKeyStatus(String networkCode, String keyType, String status,
                                       String kcv, String keyReference, String limitation,
                                       Instant observedAt) { }
