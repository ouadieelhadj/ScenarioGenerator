package com.staging.sg.swam.lis.common.model;

public enum MatchStatus {
    UNMATCHED,
    MATCHED,
    AUTH_ONLY_SUSPECT,
    LIS_ONLY,
    MATCH_PROPOSED,
    MANUALLY_MATCHED,
    MANUALLY_VALIDATED,
    PENDING_REVIEW,
    REJECTED
}
