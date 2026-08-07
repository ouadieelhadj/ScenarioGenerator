package com.staging.sg.switchlab.contracts;

import java.util.List;

public record SwitchLabTestCatalogItem(String code, String label, String moduleCode, String network,
                                       String type, String executionMode, boolean executable,
                                       List<String> requiredDataReferences) { }
