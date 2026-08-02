package com.staging.sg.deployment.model;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public record DeploymentManifest(
        String schemaVersion,
        String clientCode,
        String clientName,
        String environmentCode,
        TargetOs targetOs,
        ShellType shellType,
        String shellExecutable,
        Path deploymentRoot,
        String javaExecutable,
        DatabaseConfig database,
        List<String> memberModules,
        List<String> simulatorModules,
        Map<String, String> variables,
        Path membersBundleSource,
        Path simulatorsBundleSource,
        Path licenseFile,
        Path licensePublicKey
) {
    public DeploymentManifest {
        memberModules = memberModules == null ? List.of() : List.copyOf(memberModules);
        simulatorModules = simulatorModules == null ? List.of() : List.copyOf(simulatorModules);
        variables = variables == null ? Map.of() : Map.copyOf(variables);
        database = database == null ? DatabaseConfig.none() : database;
    }
}
