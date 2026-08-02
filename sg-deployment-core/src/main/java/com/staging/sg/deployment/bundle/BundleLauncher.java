package com.staging.sg.deployment.bundle;

import com.staging.sg.deployment.license.LicenseService;
import com.staging.sg.deployment.manifest.ManifestLoader;
import com.staging.sg.deployment.model.DeploymentManifest;
import com.staging.sg.deployment.model.ModuleSide;

import java.io.IOException;
import java.io.InputStream;
import java.io.File;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class BundleLauncher {
    public static void main(String[] args) {
        try {
            new BundleLauncher().run(args);
        } catch (Exception exception) {
            System.err.println("ERREUR BUNDLE: " + safeMessage(exception));
            System.exit(2);
        }
    }

    void run(String[] args) throws Exception {
        if (args.length == 0 || !"run".equalsIgnoreCase(args[0])) {
            throw new IllegalArgumentException("Commande attendue: run");
        }
        Map<String, String> options = options(args);
        Path manifestPath = requiredPath(options, "manifest");
        Path licensePath = requiredPath(options, "license");
        Path publicKeyPath = requiredPath(options, "public-key");
        DeploymentManifest manifest = new ManifestLoader().load(manifestPath);
        var license = new LicenseService().verify(licensePath, publicKeyPath);
        new LicenseService().requireMatches(license, manifest);

        BundleDefinition definition = definition();
        List<String> selected = definition.side == ModuleSide.MEMBER
                ? manifest.memberModules() : manifest.simulatorModules();
        if (selected.isEmpty()) throw new IllegalArgumentException("Aucun module sélectionné pour ce bundle");

        Path root = manifest.deploymentRoot().toAbsolutePath().normalize();
        Path modulesDirectory = root.resolve("modules").resolve(definition.side.name().toLowerCase());
        Path librariesDirectory = root.resolve("lib").resolve(definition.side.name().toLowerCase());
        Path logsDirectory = root.resolve("logs").resolve(definition.side.name().toLowerCase());
        Path stateDirectory = root.resolve("state").resolve(definition.side.name().toLowerCase());
        Files.createDirectories(modulesDirectory);
        Files.createDirectories(librariesDirectory);
        Files.createDirectories(logsDirectory);
        Files.createDirectories(stateDirectory);
        extractLibraries(librariesDirectory);
        Map<String, String> runtimeEnvironment = loadEnvironment(root.resolve("config/runtime.env"));

        List<Process> processes = new ArrayList<>();
        for (String code : selected) {
            ModuleRuntime module = definition.modules.get(code);
            if (module == null) throw new IllegalArgumentException("Module absent du bundle: " + code);
            Path moduleJar = extract(module, modulesDirectory);
            String classpath = moduleJar + File.pathSeparator + librariesDirectory + File.separator + "*";
            ProcessBuilder builder = new ProcessBuilder(javaExecutable(),
                    "-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8",
                    "-cp", classpath, module.mainClass);
            builder.directory(root.toFile());
            builder.environment().putAll(runtimeEnvironment);
            builder.redirectErrorStream(true);
            builder.redirectOutput(ProcessBuilder.Redirect.appendTo(logsDirectory.resolve(code + ".log").toFile()));
            Process process = builder.start();
            Files.writeString(stateDirectory.resolve(code + ".pid"), Long.toString(process.pid()));
            processes.add(process);
            System.out.println(code + " démarré pid=" + process.pid());
        }

        Runtime.getRuntime().addShutdownHook(new Thread(() -> processes.stream()
                .filter(Process::isAlive).forEach(Process::destroy), "bundle-shutdown"));
        while (processes.stream().anyMatch(Process::isAlive)) {
            Thread.sleep(1_000);
        }
        throw new IllegalStateException("Tous les modules du bundle sont arrêtés");
    }

    private BundleDefinition definition() throws IOException {
        Properties bundle = properties("/bundle.properties");
        Properties modules = properties("/bundle-modules.properties");
        ModuleSide side = ModuleSide.valueOf(bundle.getProperty("side"));
        Map<String, ModuleRuntime> runtimes = new LinkedHashMap<>();
        for (String code : modules.stringPropertyNames()) {
            String[] parts = modules.getProperty(code).split("\\|", -1);
            if (parts.length != 2) throw new IllegalStateException("Catalogue bundle invalide pour " + code);
            runtimes.put(code, new ModuleRuntime(code, parts[0], parts[1]));
        }
        return new BundleDefinition(side, Map.copyOf(runtimes));
    }

    private Path extract(ModuleRuntime module, Path targetDirectory) throws IOException {
        Path target = targetDirectory.resolve(module.artifact + ".jar").normalize();
        if (!target.startsWith(targetDirectory)) throw new IllegalArgumentException("Chemin de module invalide");
        copySingleEmbedded("bundled-modules/" + module.artifact + "-", ".jar.original", target);
        return target;
    }

    private void extractLibraries(Path targetDirectory) throws IOException {
        Path source = codeSource();
        if (Files.isDirectory(source)) {
            Path embedded = source.resolve("bundled-libs");
            if (!Files.isDirectory(embedded)) throw new IOException("Bibliothèques embarquées absentes");
            try (var paths = Files.list(embedded)) {
                for (Path library : paths.filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().endsWith(".jar")).toList()) {
                    Files.copy(library, targetDirectory.resolve(library.getFileName()), StandardCopyOption.REPLACE_EXISTING);
                }
            }
            return;
        }
        try (ZipFile zip = new ZipFile(source.toFile())) {
            int count = 0;
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory() || !entry.getName().startsWith("bundled-libs/") || !entry.getName().endsWith(".jar")) continue;
                Path target = targetDirectory.resolve(Path.of(entry.getName()).getFileName().toString()).normalize();
                if (!target.startsWith(targetDirectory)) throw new IOException("Chemin de bibliothèque invalide");
                try (InputStream input = zip.getInputStream(entry)) {
                    Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
                }
                count++;
            }
            if (count == 0) throw new IOException("Bibliothèques embarquées absentes");
        }
    }

    private void copySingleEmbedded(String prefix, String suffix, Path target) throws IOException {
        Path source = codeSource();
        if (Files.isDirectory(source)) {
            Path embedded = source.resolve("bundled-modules");
            try (var paths = Files.list(embedded)) {
                List<Path> matches = paths.filter(Files::isRegularFile)
                        .filter(path -> ("bundled-modules/" + path.getFileName()).startsWith(prefix))
                        .filter(path -> path.getFileName().toString().endsWith(suffix)).toList();
                if (matches.size() != 1) throw new IOException("Artefact embarqué introuvable ou ambigu: " + prefix);
                Files.copy(matches.getFirst(), target, StandardCopyOption.REPLACE_EXISTING);
            }
            return;
        }
        try (ZipFile zip = new ZipFile(source.toFile())) {
            List<? extends ZipEntry> matches = zip.stream()
                    .filter(entry -> !entry.isDirectory() && entry.getName().startsWith(prefix) && entry.getName().endsWith(suffix)).toList();
            if (matches.size() != 1) throw new IOException("Artefact embarqué introuvable ou ambigu: " + prefix);
            try (InputStream input = zip.getInputStream(matches.getFirst())) {
                Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private static Path codeSource() throws IOException {
        try {
            return Path.of(BundleLauncher.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toAbsolutePath().normalize();
        } catch (URISyntaxException exception) {
            throw new IOException("Emplacement du bundle invalide", exception);
        }
    }

    private static Properties properties(String resource) throws IOException {
        Properties properties = new Properties();
        try (InputStream input = BundleLauncher.class.getResourceAsStream(resource)) {
            if (input == null) throw new IOException("Ressource bundle absente: " + resource);
            properties.load(input);
        }
        return properties;
    }

    private static Map<String, String> loadEnvironment(Path envFile) throws IOException {
        if (!Files.isRegularFile(envFile)) return Map.of();
        Map<String, String> values = new LinkedHashMap<>();
        for (String raw : Files.readAllLines(envFile, StandardCharsets.UTF_8)) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            int separator = line.indexOf('=');
            if (separator < 1) throw new IOException("Ligne runtime.env invalide");
            String key = line.substring(0, separator).trim();
            if (!key.matches("[A-Za-z_][A-Za-z0-9_]*")) throw new IOException("Nom de variable invalide");
            values.put(key, line.substring(separator + 1));
        }
        return Map.copyOf(values);
    }

    private static Map<String, String> options(String[] args) {
        Map<String, String> options = new LinkedHashMap<>();
        for (int index = 1; index < args.length; index += 2) {
            if (!args[index].startsWith("--") || index + 1 >= args.length) {
                throw new IllegalArgumentException("Options --nom valeur attendues");
            }
            options.put(args[index].substring(2), args[index + 1]);
        }
        return options;
    }

    private static Path requiredPath(Map<String, String> options, String name) {
        String value = options.get(name);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Option --" + name + " obligatoire");
        return Path.of(value).toAbsolutePath().normalize();
    }

    private static String javaExecutable() {
        String executable = System.getProperty("os.name").toLowerCase().contains("win") ? "java.exe" : "java";
        return Path.of(System.getProperty("java.home"), "bin", executable).toString();
    }

    private static String safeMessage(Exception exception) {
        return exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
    }

    private record BundleDefinition(ModuleSide side, Map<String, ModuleRuntime> modules) {}
    private record ModuleRuntime(String code, String artifact, String mainClass) {}
}
