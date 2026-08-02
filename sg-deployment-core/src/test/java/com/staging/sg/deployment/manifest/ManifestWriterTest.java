package com.staging.sg.deployment.manifest;

import com.staging.sg.deployment.model.DatabaseConfig;
import com.staging.sg.deployment.model.DatabaseType;
import com.staging.sg.deployment.model.DeploymentManifest;
import com.staging.sg.deployment.model.ShellType;
import com.staging.sg.deployment.model.TargetOs;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ManifestWriterTest {
    @TempDir Path temp;

    @Test
    void roundTripsPathsAndSecretReferences() throws Exception {
        DeploymentManifest source = new DeploymentManifest("1", "BANK", "Banque d'essai", "LOCAL",
                TargetOs.WINDOWS, ShellType.GIT_BASH, "D:\\Git\\bash.exe", temp.resolve("runtime"),
                "D:\\Java\\java.exe", new DatabaseConfig(DatabaseType.POSTGRESQL, "localhost", 5432,
                "scenario", "public", "user", "secret://db/password", null, null),
                List.of("THREE_DS_MEMBER"), List.of("THREE_DS_NETWORK_SIMULATOR"),
                Map.of("DB_PASSWORD", "secret://runtime/db"), temp.resolve("members.jar"),
                temp.resolve("simulators.jar"), temp.resolve("license.sig"), temp.resolve("public.pem"));
        Path written = new ManifestWriter().write(source, temp.resolve("deployment.yml"));
        DeploymentManifest loaded = new ManifestLoader().load(written);
        assertEquals(source.clientName(), loaded.clientName());
        assertEquals(source.variables(), loaded.variables());
        assertEquals(source.javaExecutable(), loaded.javaExecutable());
    }
}
