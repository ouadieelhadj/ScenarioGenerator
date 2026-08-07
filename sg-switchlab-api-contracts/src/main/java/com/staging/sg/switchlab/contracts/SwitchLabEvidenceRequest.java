package com.staging.sg.switchlab.contracts;

public record SwitchLabEvidenceRequest(String sourceType, String name, String sourceReference,
                                       int total, int passed, int failed) { }
