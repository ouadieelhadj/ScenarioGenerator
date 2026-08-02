package com.staging.sg.deployment.license;

public record SignedLicenseEnvelope(String algorithm, String payload, String signature) {}
