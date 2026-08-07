package com.staging.sg.switchlab.contracts;

import java.util.List;

public record SwitchLabClearingNetwork(String code, String label, String moduleCode, String status,
                                       boolean uploadSupported, boolean eodSupported,
                                       boolean disputesSupported, List<String> limitations) { }
