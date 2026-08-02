package com.staging.sg.deployment.manifest;

import com.staging.sg.deployment.model.DatabaseConfig;
import com.staging.sg.deployment.model.DatabaseType;
import com.staging.sg.deployment.model.DeploymentManifest;
import com.staging.sg.deployment.model.ShellType;
import com.staging.sg.deployment.model.TargetOs;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ManifestLoader {
    private final Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));

    public DeploymentManifest load(Path manifestPath) throws IOException {
        Path absolute = manifestPath.toAbsolutePath().normalize();
        try (InputStream input = Files.newInputStream(absolute)) {
            Object parsed = yaml.load(input);
            if (!(parsed instanceof Map<?, ?> raw)) {
                throw new IllegalArgumentException("Le manifeste doit contenir un objet YAML.");
            }
            Map<String, Object> root = stringMap(raw);
            Map<String, Object> database = nested(root, "database");
            Map<String, Object> bundles = nested(root, "bundles");
            Path base = absolute.getParent();
            return new DeploymentManifest(
                    text(root, "schemaVersion", "1"),
                    text(root, "clientCode", null),
                    text(root, "clientName", null),
                    text(root, "environmentCode", null),
                    enumValue(TargetOs.class, text(root, "targetOs", null)),
                    enumValue(ShellType.class, text(root, "shellType", null)),
                    text(root, "shellExecutable", null),
                    path(base, text(root, "deploymentRoot", null)),
                    text(root, "javaExecutable", "java"),
                    database(database),
                    stringList(root.get("memberModules")),
                    stringList(root.get("simulatorModules")),
                    stringValues(nested(root, "variables")),
                    path(base, text(bundles, "members", null)),
                    path(base, text(bundles, "simulators", null)),
                    path(base, text(root, "licenseFile", null)),
                    path(base, text(root, "licensePublicKey", null))
            );
        }
    }

    private DatabaseConfig database(Map<String, Object> database) {
        if (database.isEmpty()) return DatabaseConfig.none();
        DatabaseType type = enumValue(DatabaseType.class, text(database, "type", "NONE"));
        return new DatabaseConfig(type, text(database, "host", null), integer(database.get("port")),
                text(database, "database", null), text(database, "schema", null),
                text(database, "user", null), text(database, "passwordSecretRef", null),
                text(database, "oracleServiceName", null), text(database, "oracleSid", null));
    }

    private static Map<String, Object> nested(Map<String, Object> parent, String key) {
        Object value = parent.get(key);
        return value instanceof Map<?, ?> map ? stringMap(map) : Map.of();
    }

    private static Map<String, Object> stringMap(Map<?, ?> raw) {
        Map<String, Object> result = new LinkedHashMap<>();
        raw.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }

    private static Map<String, String> stringValues(Map<String, Object> raw) {
        Map<String, String> result = new LinkedHashMap<>();
        raw.forEach((key, value) -> result.put(key, value == null ? "" : String.valueOf(value)));
        return result;
    }

    private static List<String> stringList(Object value) {
        if (!(value instanceof List<?> list)) return List.of();
        return list.stream().map(String::valueOf).toList();
    }

    private static String text(Map<String, Object> values, String key, String fallback) {
        Object value = values.get(key);
        return value == null ? fallback : String.valueOf(value).trim();
    }

    private static Integer integer(Object value) {
        return value == null ? null : Integer.valueOf(String.valueOf(value));
    }

    private static Path path(Path base, String value) {
        if (value == null || value.isBlank()) return null;
        Path path = Path.of(value);
        return path.isAbsolute() ? path.normalize() : base.resolve(path).normalize();
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, String value) {
        if (value == null || value.isBlank()) return null;
        return Enum.valueOf(type, value.trim().toUpperCase());
    }
}
