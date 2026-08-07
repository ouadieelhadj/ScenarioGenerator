package com.staging.sg.switchlab.contracts;

import java.util.List;

public record SwitchLabEcommerceComponent(String code, String label, String moduleCode, String status,
                                          List<String> capabilities, List<String> limitations) { }
