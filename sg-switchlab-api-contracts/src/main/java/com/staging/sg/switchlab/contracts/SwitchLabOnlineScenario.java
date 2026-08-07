package com.staging.sg.switchlab.contracts;

public record SwitchLabOnlineScenario(String code, String networkCode, String label,
                                      String outcome, boolean executable, String limitation) { }
