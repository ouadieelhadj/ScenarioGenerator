package com.staging.sg.acquirer.deployment;

import com.staging.sg.deployment.catalog.ModuleDescriptor;
import com.staging.sg.deployment.validation.ValidationCheck;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class DeploymentDtos {
    private DeploymentDtos() {}

    public record ClientDto(Long id, String code, String legalName, String commercialName,
                            String countryCode, String currencyCode, String status) {}
    public record CreateClientRequest(String code, String legalName, String commercialName,
                                      String countryCode, String currencyCode) {}

    public record EnvironmentDto(Long id, Long clientId, String code, String environmentType,
                                 String targetOs, String shellType, String shellExecutable,
                                 String deploymentRoot, String javaExecutable, String databaseType,
                                 String databaseHost, Integer databasePort, String databaseName,
                                 String databaseSchema, String databaseUser,
                                 String databasePasswordSecretRef, String oracleServiceName,
                                 String oracleSid, List<String> memberModules,
                                 List<String> simulatorModules, Map<String,String> variableReferences,
                                 String membersBundlePath, String simulatorsBundlePath,
                                 String licensePath, String licensePublicKeyPath) {}

    public record CreateEnvironmentRequest(Long clientId, String code, String environmentType,
                                           String targetOs, String shellType, String shellExecutable,
                                           String deploymentRoot, String javaExecutable, String databaseType,
                                           String databaseHost, Integer databasePort, String databaseName,
                                           String databaseSchema, String databaseUser,
                                           String databasePasswordSecretRef, String oracleServiceName,
                                           String oracleSid, List<String> memberModules,
                                           List<String> simulatorModules, Map<String,String> variableReferences,
                                           String membersBundlePath, String simulatorsBundlePath,
                                           String licensePath, String licensePublicKeyPath) {}

    public record PreflightDto(String executionId, Instant checkedAt, String verdict,
                               List<ValidationCheck> checks) {}
    public record CatalogDto(List<ModuleDescriptor> modules, List<String> targetOperatingSystems,
                             Map<String,List<String>> compatibleShells,
                             List<String> databaseTypes) {}

    public record LicenseDto(UUID id, Long clientId, Long environmentId, String status,
                             LocalDate validFrom, LocalDate validUntil, List<String> memberModules,
                             List<String> simulatorModules, String bundleVersion,
                             String technicalLicensePath, String pdfPath, String technicalSha256,
                             String preparedBy, String approvedBy, Instant createdAt, Instant approvedAt) {}
    public record CreateLicenseRequest(Long environmentId, LocalDate validFrom,
                                       LocalDate validUntil, String bundleVersion) {}

    public record ExecutionDto(UUID id, Long environmentId, String action, String status,
                               String requestedBy, String approvedBy, Instant startedAt,
                               Instant finishedAt, Map<String,Object> detail, Instant createdAt) {}
    public record CreateExecutionRequest(Long environmentId, String action) {}
}
