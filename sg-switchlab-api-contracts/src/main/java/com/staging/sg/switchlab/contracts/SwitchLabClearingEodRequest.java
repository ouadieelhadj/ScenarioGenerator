package com.staging.sg.switchlab.contracts;

import java.time.LocalDate;

public record SwitchLabClearingEodRequest(String networkCode, LocalDate businessDate) { }
