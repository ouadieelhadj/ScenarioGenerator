package com.staging.sg.switchlab.contracts;

import java.util.List;

public record SwitchLabOnlineNetwork(String code, String label, String moduleCode, String status,
                                     boolean sessionsSupported, boolean keyStatusSupported,
                                     boolean transactionsSupported, List<String> limitations) { }
