package com.staging.sg.deployment.license;

import java.util.List;

public record TechnicalLicense(
        String licenseId,
        String clientCode,
        String clientName,
        String environmentCode,
        String issuedAt,
        String validFrom,
        String validUntil,
        List<String> memberModules,
        List<String> simulatorModules,
        String bundleVersion,
        String approvedBy,
        boolean localTest
) {}
