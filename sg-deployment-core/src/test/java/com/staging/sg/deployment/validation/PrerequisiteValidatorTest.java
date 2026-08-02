package com.staging.sg.deployment.validation;

import com.staging.sg.deployment.catalog.ModuleCatalog;
import com.staging.sg.deployment.catalog.ModuleDescriptor;
import com.staging.sg.deployment.license.LicenseService;
import com.staging.sg.deployment.license.TechnicalLicense;
import com.staging.sg.deployment.model.DatabaseConfig;
import com.staging.sg.deployment.model.DeploymentManifest;
import com.staging.sg.deployment.model.ModuleSide;
import com.staging.sg.deployment.model.ShellType;
import com.staging.sg.deployment.model.TargetOs;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrerequisiteValidatorTest {
    @TempDir Path temp;

    @Test
    void validatesShellBundlesVariablesAndSignedLicense() throws Exception {
        Path bundle = Files.write(temp.resolve("members.jar"), new byte[]{1, 2, 3});
        LicenseService licenses = new LicenseService();
        var pair = licenses.generateKeyPair();
        Path privateKey = temp.resolve("private.pem");
        Path publicKey = temp.resolve("public.pem");
        Path signed = temp.resolve("license.json.sig");
        licenses.writePrivateKey(privateKey, pair.getPrivate());
        licenses.writePublicKey(publicKey, pair.getPublic());
        LocalDate today = LocalDate.now();
        licenses.issue(new TechnicalLicense(UUID.randomUUID().toString(), "BANK", "Bank",
                        "LOCAL", Instant.now().toString(), today.minusDays(1).toString(),
                        today.plusDays(1).toString(), List.of("MEMBER"), List.of(), "1", "checker", true),
                licenses.readPrivateKey(privateKey), signed);

        DeploymentManifest manifest = new DeploymentManifest("1", "BANK", "Bank", "LOCAL",
                currentOs(), currentShell(), null, temp.resolve("deployment"), javaExecutable(),
                DatabaseConfig.none(), List.of("MEMBER"), List.of(),
                Map.of("REQUIRED_SECRET", "secret://test/value"), bundle, null, signed, publicKey);
        ModuleCatalog catalog = ModuleCatalog.of(List.of(new ModuleDescriptor("MEMBER", "Member",
                ModuleSide.MEMBER, "member", "example.Main", null, List.of("REQUIRED_SECRET"))));

        ValidationReport report = new PrerequisiteValidator(catalog).validate(manifest);
        assertFalse(report.hasBlocking(), () -> report.checks().toString());
        assertTrue(report.checks().stream().anyMatch(check -> check.code().equals("LICENSE")
                && check.status() == CheckStatus.OK));
    }

    @Test
    void blocksIncompatibleShellAndMissingSecretReference() {
        DeploymentManifest manifest = new DeploymentManifest("1", "BANK", "Bank", "LOCAL",
                TargetOs.LINUX, ShellType.POWERSHELL, null, temp, javaExecutable(), DatabaseConfig.none(),
                List.of("MEMBER"), List.of(), Map.of(), temp.resolve("missing.jar"), null,
                temp.resolve("missing-license"), temp.resolve("missing-public"));
        ModuleCatalog catalog = ModuleCatalog.of(List.of(new ModuleDescriptor("MEMBER", "Member",
                ModuleSide.MEMBER, "member", "example.Main", null, List.of("REQUIRED_SECRET"))));

        ValidationReport report = new PrerequisiteValidator(catalog).validate(manifest);
        assertTrue(report.hasBlocking());
        assertTrue(report.checks().stream().anyMatch(check -> check.code().equals("SHELL")
                && check.status() == CheckStatus.BLOCKING));
    }

    private static TargetOs currentOs() {
        return System.getProperty("os.name").toLowerCase().contains("win") ? TargetOs.WINDOWS : TargetOs.LINUX;
    }

    private static ShellType currentShell() {
        return currentOs() == TargetOs.WINDOWS ? ShellType.POWERSHELL : ShellType.BASH_LINUX;
    }

    private static String javaExecutable() {
        String executable = currentOs() == TargetOs.WINDOWS ? "java.exe" : "java";
        return Path.of(System.getProperty("java.home"), "bin", executable).toString();
    }
}
