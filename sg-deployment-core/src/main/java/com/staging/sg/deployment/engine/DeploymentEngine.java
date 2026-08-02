package com.staging.sg.deployment.engine;

import com.staging.sg.deployment.model.DeploymentManifest;
import com.staging.sg.deployment.manifest.ManifestWriter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class DeploymentEngine {
    public DeploymentPlan plan(DeploymentManifest manifest) {
        List<String> actions = new ArrayList<>();
        actions.add("Créer ou vérifier l'arborescence sous " + manifest.deploymentRoot());
        if (!manifest.memberModules().isEmpty()) {
            actions.add("Installer scenario-members-bundle.jar pour " + String.join(", ", manifest.memberModules()));
        }
        if (!manifest.simulatorModules().isEmpty()) {
            actions.add("Installer scenario-simulators-bundle.jar pour " + String.join(", ", manifest.simulatorModules()));
        }
        actions.add("Installer la licence technique et sa clé publique");
        actions.add("Conserver les secrets hors du manifeste et des artefacts");
        actions.add("Exécuter les health-checks après démarrage");
        return new DeploymentPlan(manifest.clientCode(), manifest.environmentCode(), List.copyOf(actions));
    }

    public void install(DeploymentManifest manifest, Path sourceManifest) throws IOException {
        Layout layout = layout(manifest);
        createLayout(layout);
        if (!manifest.memberModules().isEmpty()) {
            copyRequired(manifest.membersBundleSource(), layout.artifacts.resolve("scenario-members-bundle.jar"));
        }
        if (!manifest.simulatorModules().isEmpty()) {
            copyRequired(manifest.simulatorsBundleSource(), layout.artifacts.resolve("scenario-simulators-bundle.jar"));
        }
        copyRequired(manifest.licenseFile(), layout.config.resolve("license.json.sig"));
        copyRequired(manifest.licensePublicKey(), layout.config.resolve("license-public.pem"));
        // Le manifeste installé est réécrit avec les chemins déjà résolus. Une
        // simple copie ferait recalculer les chemins relatifs depuis config/.
        new ManifestWriter().write(manifest, layout.config.resolve("deployment-manifest.yml"));
        Files.writeString(layout.state.resolve("installed-at.txt"), Instant.now().toString());
    }

    public Map<String, Long> start(DeploymentManifest manifest) throws IOException {
        Layout layout = layout(manifest);
        createLayout(layout);
        Map<String, Long> started = new LinkedHashMap<>();
        if (!manifest.memberModules().isEmpty()) {
            started.put("members", startBundle(manifest, layout, "members",
                    layout.artifacts.resolve("scenario-members-bundle.jar")));
        }
        if (!manifest.simulatorModules().isEmpty()) {
            started.put("simulators", startBundle(manifest, layout, "simulators",
                    layout.artifacts.resolve("scenario-simulators-bundle.jar")));
        }
        return started;
    }

    public Map<String, String> status(DeploymentManifest manifest) throws IOException {
        Layout layout = layout(manifest);
        Map<String, String> result = new LinkedHashMap<>();
        for (String name : List.of("members", "simulators")) {
            Path pidFile = layout.state.resolve(name + ".pid");
            if (!Files.isRegularFile(pidFile)) {
                result.put(name, "NOT_STARTED");
                continue;
            }
            long pid = Long.parseLong(Files.readString(pidFile).trim());
            result.put(name, ProcessHandle.of(pid).filter(ProcessHandle::isAlive).isPresent()
                    ? "RUNNING pid=" + pid : "STOPPED pid=" + pid);
        }
        return result;
    }

    public Map<String, String> stop(DeploymentManifest manifest) throws IOException {
        Layout layout = layout(manifest);
        Map<String, String> result = new LinkedHashMap<>();
        for (String name : List.of("members", "simulators")) {
            Path pidFile = layout.state.resolve(name + ".pid");
            if (!Files.isRegularFile(pidFile)) {
                result.put(name, "NOT_STARTED");
                continue;
            }
            long pid = Long.parseLong(Files.readString(pidFile).trim());
            Optional<ProcessHandle> handle = ProcessHandle.of(pid);
            if (handle.isPresent() && handle.get().isAlive()) {
                stopProcessTree(handle.get());
                result.put(name, "STOP_REQUESTED pid=" + pid);
            } else {
                result.put(name, "ALREADY_STOPPED pid=" + pid);
            }
        }
        return result;
    }

    public void upgrade(DeploymentManifest manifest, Path sourceManifest) throws IOException {
        Layout layout = layout(manifest);
        createLayout(layout);
        String stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
                .withZone(java.time.ZoneOffset.UTC).format(Instant.now());
        Path backup = layout.backups.resolve(stamp);
        Files.createDirectories(backup);
        backupIfPresent(layout.artifacts.resolve("scenario-members-bundle.jar"), backup);
        backupIfPresent(layout.artifacts.resolve("scenario-simulators-bundle.jar"), backup);
        backupIfPresent(layout.config.resolve("license.json.sig"), backup);
        backupIfPresent(layout.config.resolve("license-public.pem"), backup);
        backupIfPresent(layout.config.resolve("deployment-manifest.yml"), backup);
        install(manifest, sourceManifest);
    }

    public Path rollback(DeploymentManifest manifest) throws IOException {
        Layout layout = layout(manifest);
        if (!Files.isDirectory(layout.backups)) throw new IOException("Aucune sauvegarde disponible");
        Path latest;
        try (var entries = Files.list(layout.backups)) {
            latest = entries.filter(Files::isDirectory).max(Comparator.comparing(Path::getFileName))
                    .orElseThrow(() -> new IOException("Aucune sauvegarde disponible"));
        }
        restoreIfPresent(latest.resolve("scenario-members-bundle.jar"), layout.artifacts);
        restoreIfPresent(latest.resolve("scenario-simulators-bundle.jar"), layout.artifacts);
        restoreIfPresent(latest.resolve("license.json.sig"), layout.config);
        restoreIfPresent(latest.resolve("license-public.pem"), layout.config);
        restoreIfPresent(latest.resolve("deployment-manifest.yml"), layout.config);
        Files.writeString(layout.state.resolve("rolled-back-at.txt"), Instant.now() + " " + latest.getFileName());
        return latest;
    }

    public List<String> logs(DeploymentManifest manifest, String bundle, int maxLines) throws IOException {
        Path log = layout(manifest).logs.resolve("bundle-" + safeBundle(bundle) + ".log");
        if (!Files.isRegularFile(log)) return List.of("Aucun journal pour " + bundle);
        // Les journaux peuvent provenir d'une console Windows utilisant un encodage
        // historique. Le remplacement des octets invalides garde la commande logs
        // exploitable sans jamais interrompre l'administration du déploiement.
        List<String> lines = new String(Files.readAllBytes(log), StandardCharsets.UTF_8).lines().toList();
        return lines.subList(Math.max(0, lines.size() - Math.max(1, maxLines)), lines.size());
    }

    private long startBundle(DeploymentManifest manifest, Layout layout, String name, Path jar) throws IOException {
        if (!Files.isRegularFile(jar)) throw new IOException("Bundle introuvable: " + name);
        Path pidFile = layout.state.resolve(name + ".pid");
        if (Files.isRegularFile(pidFile)) {
            long existing = Long.parseLong(Files.readString(pidFile).trim());
            if (ProcessHandle.of(existing).filter(ProcessHandle::isAlive).isPresent()) {
                throw new IOException("Bundle déjà démarré: " + name);
            }
        }
        Process process = new ProcessBuilder(manifest.javaExecutable(),
                "-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8",
                "-jar", jar.toString(), "run",
                "--manifest", layout.config.resolve("deployment-manifest.yml").toString(),
                "--license", layout.config.resolve("license.json.sig").toString(),
                "--public-key", layout.config.resolve("license-public.pem").toString())
                .directory(layout.root.toFile())
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.appendTo(layout.logs.resolve("bundle-" + name + ".log").toFile()))
                .start();
        Files.writeString(pidFile, Long.toString(process.pid()));
        return process.pid();
    }

    private static void createLayout(Layout layout) throws IOException {
        Files.createDirectories(layout.artifacts);
        Files.createDirectories(layout.config);
        Files.createDirectories(layout.logs);
        Files.createDirectories(layout.backups);
        Files.createDirectories(layout.scripts);
        Files.createDirectories(layout.state);
    }

    private static Layout layout(DeploymentManifest manifest) {
        Path root = manifest.deploymentRoot().toAbsolutePath().normalize();
        if (root.getNameCount() < 3) throw new IllegalArgumentException("Répertoire de déploiement trop large");
        return new Layout(root, root.resolve("artifacts"), root.resolve("config"), root.resolve("logs"),
                root.resolve("backups"), root.resolve("scripts"), root.resolve("state"));
    }

    private static void copyRequired(Path source, Path target) throws IOException {
        if (source == null || !Files.isRegularFile(source)) throw new IOException("Source introuvable");
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
    }

    private static void backupIfPresent(Path source, Path backup) throws IOException {
        if (Files.isRegularFile(source)) Files.copy(source, backup.resolve(source.getFileName()),
                StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
    }

    private static void restoreIfPresent(Path source, Path targetDirectory) throws IOException {
        if (Files.isRegularFile(source)) Files.copy(source, targetDirectory.resolve(source.getFileName()),
                StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
    }

    private static void stopProcessTree(ProcessHandle root) throws IOException {
        List<ProcessHandle> descendants = root.descendants().toList();
        descendants.stream().filter(ProcessHandle::isAlive).forEach(ProcessHandle::destroy);
        root.destroy();
        try {
            Thread.sleep(1_000);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("Arrêt interrompu", exception);
        }
        descendants.stream().filter(ProcessHandle::isAlive).forEach(ProcessHandle::destroyForcibly);
        if (root.isAlive()) root.destroyForcibly();
    }

    private static String safeBundle(String bundle) {
        if (!List.of("members", "simulators").contains(bundle)) {
            throw new IllegalArgumentException("Bundle attendu: members ou simulators");
        }
        return bundle;
    }

    private record Layout(Path root, Path artifacts, Path config, Path logs,
                          Path backups, Path scripts, Path state) {}
}
