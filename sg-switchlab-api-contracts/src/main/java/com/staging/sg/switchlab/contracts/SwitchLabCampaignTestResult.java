package com.staging.sg.switchlab.contracts;

public record SwitchLabCampaignTestResult(String testCode, String moduleCode, String expected,
                                          String actual, String verdict, long elapsedMillis,
                                          int sampleCount, int successCount, int errorCount,
                                          long p95ResponseTimeMs) { }
