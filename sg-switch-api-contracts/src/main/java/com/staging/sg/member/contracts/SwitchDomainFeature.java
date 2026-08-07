package com.staging.sg.member.contracts;

public record SwitchDomainFeature(
        String code,
        String label,
        String status,
        boolean backendEndpointAvailable,
        boolean consultationAvailable,
        boolean actionAvailable,
        boolean makerCheckerRequired,
        String limitation) {
}
