package com.staging.sg.common.iso.sid;

import java.util.List;

public class SidValidationException extends Exception {
    private final List<String> violations;

    public SidValidationException(List<String> violations) {
        super(String.join("; ", violations));
        this.violations = List.copyOf(violations);
    }

    public List<String> getViolations() {
        return violations;
    }
}
