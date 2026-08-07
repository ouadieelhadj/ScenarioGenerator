package com.staging.sg.switchlab.contracts;

public record SwitchLabAuthorizedAction(String code, boolean allowed, String reason) {
}
