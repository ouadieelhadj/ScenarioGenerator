package com.staging.sg.swam.lis.common.model;

import java.util.List;

public record ParsedLisFile(
        String originatorBankCode, String destinationBankCode, String processingDate,
        int fileSequence, int physicalRecordCount, List<LisFinancialRecord> financialRecords) {
}
