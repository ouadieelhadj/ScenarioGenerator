package com.staging.sg.deployment.manifest;

import com.staging.sg.deployment.model.DatabaseConfig;
import com.staging.sg.deployment.model.DeploymentManifest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** Writes a portable YAML manifest containing configuration and secret references only. */
public final class ManifestWriter {
    public Path write(DeploymentManifest manifest, Path output) throws IOException {
        Path absolute = output.toAbsolutePath().normalize();
        Files.createDirectories(absolute.getParent());
        StringBuilder yaml = new StringBuilder();
        scalar(yaml, "schemaVersion", manifest.schemaVersion());
        scalar(yaml, "clientCode", manifest.clientCode());
        scalar(yaml, "clientName", manifest.clientName());
        scalar(yaml, "environmentCode", manifest.environmentCode());
        scalar(yaml, "targetOs", name(manifest.targetOs()));
        scalar(yaml, "shellType", name(manifest.shellType()));
        scalar(yaml, "shellExecutable", manifest.shellExecutable());
        scalar(yaml, "deploymentRoot", path(manifest.deploymentRoot()));
        scalar(yaml, "javaExecutable", manifest.javaExecutable());
        database(yaml, manifest.database());
        list(yaml, "memberModules", manifest.memberModules());
        list(yaml, "simulatorModules", manifest.simulatorModules());
        if (manifest.variables().isEmpty()) yaml.append("variables: {}\n");
        else {
            yaml.append("variables:\n");
            manifest.variables().forEach((key, value) -> yaml.append("  ").append(key).append(": ").append(quote(value)).append('\n'));
        }
        yaml.append("bundles:\n");
        yaml.append("  members: ").append(quote(path(manifest.membersBundleSource()))).append('\n');
        yaml.append("  simulators: ").append(quote(path(manifest.simulatorsBundleSource()))).append('\n');
        scalar(yaml, "licenseFile", path(manifest.licenseFile()));
        scalar(yaml, "licensePublicKey", path(manifest.licensePublicKey()));
        Files.writeString(absolute, yaml.toString(), StandardCharsets.UTF_8);
        return absolute;
    }

    private static void database(StringBuilder yaml, DatabaseConfig database) {
        yaml.append("database:\n");
        yaml.append("  type: ").append(quote(name(database.type()))).append('\n');
        nested(yaml, "host", database.host());
        if (database.port() != null) yaml.append("  port: ").append(database.port()).append('\n');
        nested(yaml, "database", database.database());
        nested(yaml, "schema", database.schema());
        nested(yaml, "user", database.user());
        nested(yaml, "passwordSecretRef", database.passwordSecretRef());
        nested(yaml, "oracleServiceName", database.oracleServiceName());
        nested(yaml, "oracleSid", database.oracleSid());
    }

    private static void list(StringBuilder yaml, String key, List<String> values) {
        yaml.append(key).append(":");
        if (values.isEmpty()) yaml.append(" []\n");
        else {
            yaml.append('\n');
            values.forEach(value -> yaml.append("  - ").append(quote(value)).append('\n'));
        }
    }

    private static void scalar(StringBuilder yaml, String key, String value) {
        yaml.append(key).append(": ").append(quote(value)).append('\n');
    }

    private static void nested(StringBuilder yaml, String key, String value) {
        if (value != null && !value.isBlank()) yaml.append("  ").append(key).append(": ").append(quote(value)).append('\n');
    }

    private static String quote(String value) {
        return value == null ? "''" : "'" + value.replace("'", "''") + "'";
    }

    private static String name(Enum<?> value) { return value == null ? null : value.name(); }
    private static String path(Path value) { return value == null ? null : value.toString(); }
}
