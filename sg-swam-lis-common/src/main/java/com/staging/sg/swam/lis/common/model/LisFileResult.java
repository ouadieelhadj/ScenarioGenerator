package com.staging.sg.swam.lis.common.model;

public record LisFileResult(
        Long fileId, String fileName, String storagePath, String sha256,
        long byteSize, int physicalRecordCount, String status) {
}
