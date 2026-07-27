package com.staging.sg.swam.lis.common.model;

public record LisImportResult(
        Long fileId, int recordsRead, long matched, long lisOnly,
        long readyForAccounting, String status) {
}
