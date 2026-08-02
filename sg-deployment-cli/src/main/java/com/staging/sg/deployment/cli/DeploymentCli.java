package com.staging.sg.deployment.cli;

import com.staging.sg.deployment.catalog.ModuleCatalog;
import com.staging.sg.deployment.engine.DeploymentEngine;
import com.staging.sg.deployment.license.LicensePdfService;
import com.staging.sg.deployment.license.LicenseService;
import com.staging.sg.deployment.license.TechnicalLicense;
import com.staging.sg.deployment.manifest.ManifestLoader;
import com.staging.sg.deployment.model.DeploymentManifest;
import com.staging.sg.deployment.validation.PrerequisiteValidator;
import com.staging.sg.deployment.validation.ValidationReport;

import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class DeploymentCli {
    private final ManifestLoader manifests = new ManifestLoader();
    private final PrerequisiteValidator validator = new PrerequisiteValidator(ModuleCatalog.scenarioGenerator());
    private final DeploymentEngine engine = new DeploymentEngine();
    private final LicenseService licenses = new LicenseService();

    public static void main(String[] args) {
        int result = new DeploymentCli().run(args, System.out, System.err);
        if (result != 0) System.exit(result);
    }

    public int run(String[] args, PrintStream out, PrintStream err) {
        if (args.length == 0 || "help".equalsIgnoreCase(args[0]) || "--help".equalsIgnoreCase(args[0])) {
            usage(out);
            return 0;
        }
        String command = args[0].toLowerCase();
        Map<String, String> options;
        try {
            options = options(Arrays.copyOfRange(args, 1, args.length));
            return switch (command) {
                case "keygen" -> keygen(options, out);
                case "license-issue" -> licenseIssue(options, out);
                case "validate", "plan", "install", "start", "status", "stop",
                        "upgrade", "rollback", "logs" -> deployment(command, options, out);
                default -> {
                    err.println("Commande non autorisée: " + command);
                    usage(err);
                    yield 64;
                }
            };
        } catch (Exception exception) {
            err.println("ERREUR: " + safeMessage(exception));
            return 2;
        }
    }

    private int deployment(String command, Map<String, String> options, PrintStream out) throws Exception {
        Path manifestPath = requiredPath(options, "manifest");
        DeploymentManifest manifest = manifests.load(manifestPath);
        if ("status".equals(command)) {
            engine.status(manifest).forEach((name, status) -> out.println(name + ": " + status));
            return 0;
        }
        if ("stop".equals(command)) {
            engine.stop(manifest).forEach((name, status) -> out.println(name + ": " + status));
            return 0;
        }
        if ("logs".equals(command)) {
            String bundle = options.getOrDefault("bundle", "members");
            int lines = Integer.parseInt(options.getOrDefault("lines", "100"));
            engine.logs(manifest, bundle, lines).forEach(out::println);
            return 0;
        }

        ValidationReport report = validator.validate(manifest);
        print(report, out);
        if (report.hasBlocking()) {
            out.println("VERDICT: BLOCKING - opération interdite");
            return 3;
        }
        out.println("VERDICT: READY");
        switch (command) {
            case "validate" -> { return 0; }
            case "plan" -> engine.plan(manifest).actions().forEach(action -> out.println("- " + action));
            case "install" -> engine.install(manifest, manifestPath);
            case "start" -> engine.start(manifest).forEach((name, pid) -> out.println(name + " démarré pid=" + pid));
            case "upgrade" -> engine.upgrade(manifest, manifestPath);
            case "rollback" -> out.println("Sauvegarde restaurée: " + engine.rollback(manifest));
            default -> throw new IllegalArgumentException("Commande non prise en charge");
        }
        return 0;
    }

    private int keygen(Map<String, String> options, PrintStream out) throws Exception {
        Path privateKey = requiredPath(options, "private-key");
        Path publicKey = requiredPath(options, "public-key");
        if (Files.exists(privateKey) || Files.exists(publicKey)) {
            throw new IllegalArgumentException("Refus d'écraser une clé existante");
        }
        var pair = licenses.generateKeyPair();
        licenses.writePrivateKey(privateKey, pair.getPrivate());
        licenses.writePublicKey(publicKey, pair.getPublic());
        out.println("Paire de clés locale générée. La clé privée ne doit jamais être versionnée.");
        return 0;
    }

    private int licenseIssue(Map<String, String> options, PrintStream out) throws Exception {
        Path manifestPath = requiredPath(options, "manifest");
        DeploymentManifest manifest = manifests.load(manifestPath);
        Path privateKey = requiredPath(options, "private-key");
        Path output = requiredPath(options, "output-dir");
        LocalDate validUntil = LocalDate.parse(required(options, "valid-until"));
        LocalDate validFrom = LocalDate.parse(options.getOrDefault("valid-from", LocalDate.now().toString()));
        String approvedBy = required(options, "approved-by");
        String bundleVersion = required(options, "bundle-version");
        if (!validUntil.isAfter(validFrom)) throw new IllegalArgumentException("La date de fin doit suivre la date de début");
        TechnicalLicense license = new TechnicalLicense(UUID.randomUUID().toString(),
                manifest.clientCode(), manifest.clientName(), manifest.environmentCode(),
                Instant.now().toString(), validFrom.toString(), validUntil.toString(),
                manifest.memberModules(), manifest.simulatorModules(), bundleVersion, approvedBy,
                Boolean.parseBoolean(options.getOrDefault("local-test", "false")));
        Path signed = output.resolve("license.json.sig");
        Path pdf = output.resolve("license.pdf");
        licenses.issue(license, licenses.readPrivateKey(privateKey), signed);
        new LicensePdfService().generate(license, signed, pdf);
        out.println("Licence technique générée: " + signed);
        out.println("Licence PDF générée: " + pdf);
        return 0;
    }

    private static Map<String, String> options(String[] args) {
        Map<String, String> values = new LinkedHashMap<>();
        for (int index = 0; index < args.length; index++) {
            String key = args[index];
            if (!key.startsWith("--")) throw new IllegalArgumentException("Option attendue, reçue: " + key);
            if (index + 1 >= args.length || args[index + 1].startsWith("--")) {
                values.put(key.substring(2), "true");
            } else {
                values.put(key.substring(2), args[++index]);
            }
        }
        return values;
    }

    private static void print(ValidationReport report, PrintStream out) {
        out.println("Validation " + report.executionId() + " - " + report.clientCode()
                + "/" + report.environmentCode() + " - shell=" + report.shellType());
        report.checks().forEach(check -> out.printf("%-9s %-28s %s%n",
                check.status(), check.code(), check.detail()));
    }

    private static Path requiredPath(Map<String, String> options, String name) {
        return Path.of(required(options, name)).toAbsolutePath().normalize();
    }

    private static String required(Map<String, String> options, String name) {
        String value = options.get(name);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Option --" + name + " obligatoire");
        return value;
    }

    private static String safeMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }

    private static void usage(PrintStream out) {
        out.println("""
                ScenarioGenerator Deployment CLI
                  validate --manifest <file>
                  plan --manifest <file>
                  install|start|status|stop|upgrade|rollback --manifest <file>
                  logs --manifest <file> --bundle members|simulators [--lines 100]
                  keygen --private-key <file> --public-key <file>
                  license-issue --manifest <file> --private-key <file> --output-dir <dir>
                    --valid-until YYYY-MM-DD --approved-by <login> --bundle-version <version>
                    [--valid-from YYYY-MM-DD] [--local-test true]
                """);
    }
}
