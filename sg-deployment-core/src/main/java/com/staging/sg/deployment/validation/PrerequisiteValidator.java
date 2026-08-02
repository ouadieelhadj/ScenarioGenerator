package com.staging.sg.deployment.validation;

import com.staging.sg.deployment.catalog.ModuleCatalog;
import com.staging.sg.deployment.catalog.ModuleDescriptor;
import com.staging.sg.deployment.license.LicenseService;
import com.staging.sg.deployment.license.TechnicalLicense;
import com.staging.sg.deployment.model.DatabaseConfig;
import com.staging.sg.deployment.model.DatabaseType;
import com.staging.sg.deployment.model.DeploymentManifest;
import com.staging.sg.deployment.model.ModuleSide;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public final class PrerequisiteValidator {
    private final ModuleCatalog catalog;
    private final LicenseService licenses;

    public PrerequisiteValidator(ModuleCatalog catalog) {
        this.catalog = catalog;
        this.licenses = new LicenseService();
    }

    public ValidationReport validate(DeploymentManifest manifest) {
        List<ValidationCheck> checks = new ArrayList<>();
        required(checks, "CLIENT", manifest.clientCode(), "Code client renseigné");
        required(checks, "ENVIRONMENT", manifest.environmentCode(), "Environnement renseigné");
        validateShell(manifest, checks);
        validateJava(manifest.javaExecutable(), checks);
        validateRoot(manifest.deploymentRoot(), checks);
        validateModules(manifest, checks);
        validateBundle(checks, "MEMBERS_BUNDLE", manifest.memberModules(), manifest.membersBundleSource());
        validateBundle(checks, "SIMULATORS_BUNDLE", manifest.simulatorModules(), manifest.simulatorsBundleSource());
        validateLicense(manifest, checks);
        validateDatabase(manifest.database(), checks);
        return ValidationReport.create(manifest.clientCode(), manifest.environmentCode(),
                manifest.shellType() == null ? null : manifest.shellType().name(), checks);
    }

    private void validateShell(DeploymentManifest manifest, List<ValidationCheck> checks) {
        if (manifest.targetOs() == null || manifest.shellType() == null) {
            checks.add(blocking("SHELL", "OS cible et type de shell obligatoires"));
            return;
        }
        if (!manifest.shellType().supports(manifest.targetOs())) {
            checks.add(blocking("SHELL", "Shell incompatible avec l'OS cible"));
            return;
        }
        String executable = manifest.shellExecutable();
        if (executable == null || executable.isBlank()) {
            checks.add(warning("SHELL", "Shell compatible; exécutable à détecter au moment de l'exécution"));
        } else if (looksLikePath(executable) && !Files.isRegularFile(Path.of(executable))) {
            checks.add(blocking("SHELL", "Exécutable du shell introuvable"));
        } else {
            checks.add(ok("SHELL", "Shell compatible et configuré"));
        }
    }

    private void validateJava(String executable, List<ValidationCheck> checks) {
        if (executable == null || executable.isBlank()) {
            checks.add(blocking("JAVA", "Exécutable Java obligatoire"));
            return;
        }
        try {
            Process process = new ProcessBuilder(executable, "-version").redirectErrorStream(true).start();
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                checks.add(blocking("JAVA", "La vérification Java dépasse 5 secondes"));
            } else if (process.exitValue() == 0) {
                String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                int feature = javaFeature(output);
                if (feature < 21) {
                    checks.add(blocking("JAVA", "Java 21 minimum requis; version détectée: " + feature));
                } else {
                    checks.add(ok("JAVA", "Java " + feature + " disponible (minimum 21)"));
                }
            } else {
                checks.add(blocking("JAVA", "Java retourne un code non nul"));
            }
        } catch (Exception exception) {
            checks.add(blocking("JAVA", "Java introuvable ou non exécutable"));
        }
    }

    private void validateRoot(Path root, List<ValidationCheck> checks) {
        if (root == null) {
            checks.add(blocking("DEPLOYMENT_ROOT", "Répertoire de déploiement obligatoire"));
            return;
        }
        Path existing = root;
        while (existing != null && !Files.exists(existing)) existing = existing.getParent();
        if (existing == null || !Files.isDirectory(existing) || !Files.isWritable(existing)) {
            checks.add(blocking("DEPLOYMENT_ROOT", "Aucun parent accessible en écriture"));
            return;
        }
        try {
            long usable = Files.getFileStore(existing).getUsableSpace();
            if (usable < 512L * 1024L * 1024L) {
                checks.add(blocking("DISK_SPACE", "Moins de 512 MiB disponibles"));
            } else {
                checks.add(ok("DISK_SPACE", "Espace disque disponible"));
            }
            checks.add(ok("DEPLOYMENT_ROOT", "Répertoire cible ou parent accessible"));
        } catch (IOException exception) {
            checks.add(warning("DISK_SPACE", "Espace disque non déterminé"));
        }
    }

    private void validateModules(DeploymentManifest manifest, List<ValidationCheck> checks) {
        Set<Integer> ports = new HashSet<>();
        validateSide(manifest.memberModules(), ModuleSide.MEMBER, manifest, checks, ports);
        validateSide(manifest.simulatorModules(), ModuleSide.SIMULATOR, manifest, checks, ports);
        if (manifest.memberModules().isEmpty() && manifest.simulatorModules().isEmpty()) {
            checks.add(blocking("MODULES", "Au moins un module doit être sélectionné"));
        }
    }

    private void validateSide(List<String> codes, ModuleSide side, DeploymentManifest manifest,
                              List<ValidationCheck> checks, Set<Integer> ports) {
        for (String code : codes) {
            ModuleDescriptor module = catalog.find(code).orElse(null);
            if (module == null || module.side() != side) {
                checks.add(blocking("MODULE_" + code, "Module inconnu ou classé du mauvais côté"));
                continue;
            }
            List<String> missing = module.requiredVariables().stream()
                    .filter(name -> !manifest.variables().containsKey(name)
                            || manifest.variables().get(name).isBlank())
                    .toList();
            if (!missing.isEmpty()) {
                checks.add(blocking("VARIABLES_" + code,
                        "Variables obligatoires absentes: " + String.join(", ", missing)));
            } else {
                checks.add(ok("MODULE_" + code, "Module connu et variables référencées"));
            }
            if (module.defaultPort() == null) {
                checks.add(warning("PORT_" + code, "Port dynamique à résoudre depuis la configuration"));
            } else if (!ports.add(module.defaultPort())) {
                checks.add(blocking("PORT_" + code, "Collision de port dans la sélection"));
            } else if (isPortAvailable(module.defaultPort())) {
                checks.add(ok("PORT_" + code, "Port disponible"));
            } else {
                checks.add(blocking("PORT_" + code, "Port déjà utilisé"));
            }
        }
    }

    private void validateBundle(List<ValidationCheck> checks, String code,
                                List<String> selectedModules, Path bundle) {
        if (selectedModules.isEmpty()) return;
        if (bundle == null || !Files.isRegularFile(bundle)) {
            checks.add(blocking(code, "Bundle demandé introuvable"));
        } else {
            checks.add(ok(code, "Bundle présent"));
        }
    }

    private void validateLicense(DeploymentManifest manifest, List<ValidationCheck> checks) {
        if (manifest.licenseFile() == null || !Files.isRegularFile(manifest.licenseFile())) {
            checks.add(blocking("LICENSE", "Licence technique signée introuvable"));
        } else if (manifest.licensePublicKey() == null || !Files.isRegularFile(manifest.licensePublicKey())) {
            checks.add(blocking("LICENSE_PUBLIC_KEY", "Clé publique de vérification introuvable"));
        } else {
            try {
                TechnicalLicense license = licenses.verify(manifest.licenseFile(), manifest.licensePublicKey());
                licenses.requireMatches(license, manifest);
                checks.add(ok("LICENSE", "Signature, validité, client, environnement et modules vérifiés"));
            } catch (Exception exception) {
                checks.add(blocking("LICENSE", "Licence invalide ou incompatible: " + exception.getMessage()));
            }
        }
    }

    private void validateDatabase(DatabaseConfig database, List<ValidationCheck> checks) {
        if (database.type() == null || database.type() == DatabaseType.NONE) {
            checks.add(warning("DATABASE", "Aucune base configurée"));
            return;
        }
        if (blank(database.host()) || database.port() == null || blank(database.user())
                || blank(database.passwordSecretRef())) {
            checks.add(blocking("DATABASE", "Paramètres ou référence du secret DB incomplets"));
            return;
        }
        if (database.type() == DatabaseType.POSTGRESQL && blank(database.database())) {
            checks.add(blocking("DATABASE", "Nom de base PostgreSQL obligatoire"));
            return;
        }
        if (database.type() == DatabaseType.ORACLE
                && blank(database.oracleServiceName()) && blank(database.oracleSid())) {
            checks.add(blocking("DATABASE", "Service Name ou SID Oracle obligatoire"));
            return;
        }
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(database.host(), database.port()), 1500);
            checks.add(ok("DATABASE", "Hôte et port de base accessibles"));
        } catch (IOException exception) {
            checks.add(blocking("DATABASE", "Hôte ou port de base inaccessible"));
        }
    }

    private static void required(List<ValidationCheck> checks, String code, String value, String okDetail) {
        checks.add(blank(value) ? blocking(code, "Valeur obligatoire absente") : ok(code, okDetail));
    }

    private static boolean isPortAvailable(int port) {
        try (ServerSocket socket = new ServerSocket()) {
            socket.setReuseAddress(false);
            socket.bind(new InetSocketAddress("127.0.0.1", port));
            return true;
        } catch (IOException exception) {
            return false;
        }
    }

    private static boolean looksLikePath(String value) {
        return value.contains("/") || value.contains("\\");
    }

    private static int javaFeature(String versionOutput) {
        int quote = versionOutput.indexOf('"');
        if (quote < 0) return 0;
        int end = versionOutput.indexOf('"', quote + 1);
        if (end < 0) return 0;
        String[] parts = versionOutput.substring(quote + 1, end).split("[._-]");
        try {
            int first = Integer.parseInt(parts[0]);
            return first == 1 && parts.length > 1 ? Integer.parseInt(parts[1]) : first;
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static ValidationCheck ok(String code, String detail) {
        return new ValidationCheck(code, CheckStatus.OK, detail);
    }

    private static ValidationCheck warning(String code, String detail) {
        return new ValidationCheck(code, CheckStatus.WARNING, detail);
    }

    private static ValidationCheck blocking(String code, String detail) {
        return new ValidationCheck(code, CheckStatus.BLOCKING, detail);
    }
}
