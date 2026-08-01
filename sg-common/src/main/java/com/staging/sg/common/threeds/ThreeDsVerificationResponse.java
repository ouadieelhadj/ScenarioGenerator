package com.staging.sg.common.threeds;

public record ThreeDsVerificationResponse(
        String schemaVersion,
        boolean valid,
        boolean replayedForSameTransaction,
        boolean sandboxEvidence) {
}
