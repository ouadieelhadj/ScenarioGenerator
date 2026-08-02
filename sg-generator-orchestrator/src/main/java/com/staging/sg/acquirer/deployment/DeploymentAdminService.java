package com.staging.sg.acquirer.deployment;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.staging.sg.deployment.catalog.ModuleCatalog;
import com.staging.sg.deployment.engine.DeploymentEngine;
import com.staging.sg.deployment.license.LicensePdfService;
import com.staging.sg.deployment.license.LicenseService;
import com.staging.sg.deployment.license.TechnicalLicense;
import com.staging.sg.deployment.manifest.ManifestWriter;
import com.staging.sg.deployment.model.DatabaseConfig;
import com.staging.sg.deployment.model.DatabaseType;
import com.staging.sg.deployment.model.DeploymentManifest;
import com.staging.sg.deployment.model.ModuleSide;
import com.staging.sg.deployment.model.ShellType;
import com.staging.sg.deployment.model.TargetOs;
import com.staging.sg.deployment.validation.CheckStatus;
import com.staging.sg.deployment.validation.PrerequisiteValidator;
import com.staging.sg.deployment.validation.ValidationReport;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.HexFormat;

import static com.staging.sg.acquirer.deployment.DeploymentDtos.*;

@Service
public class DeploymentAdminService {
    private static final Set<String> SENSITIVE_MARKERS = Set.of("PASSWORD", "SECRET", "KEY", "PEPPER", "PAN", "PIN");
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final Environment springEnvironment;
    private final ModuleCatalog catalog = ModuleCatalog.scenarioGenerator();
    private final PrerequisiteValidator validator = new PrerequisiteValidator(catalog);
    private final DeploymentEngine engine = new DeploymentEngine();
    private final LicenseService licenses = new LicenseService();

    public DeploymentAdminService(JdbcTemplate jdbc, ObjectMapper mapper, Environment springEnvironment) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.springEnvironment = springEnvironment;
    }

    public CatalogDto catalog() {
        return new CatalogDto(catalog.all(), List.of(TargetOs.WINDOWS.name(), TargetOs.LINUX.name()),
                Map.of(TargetOs.WINDOWS.name(), List.of(ShellType.GIT_BASH.name(), ShellType.POWERSHELL.name(), ShellType.CMD_WINDOWS.name()),
                        TargetOs.LINUX.name(), List.of(ShellType.BASH_LINUX.name())),
                List.of(DatabaseType.NONE.name(), DatabaseType.POSTGRESQL.name(), DatabaseType.ORACLE.name()));
    }

    public List<ClientDto> clients() {
        return jdbc.query("""
                SELECT id,code,legal_name,commercial_name,country_code,currency_code,status
                  FROM deployment_client ORDER BY code
                """, (rs, row) -> new ClientDto(rs.getLong("id"), rs.getString("code"),
                rs.getString("legal_name"), rs.getString("commercial_name"),
                rs.getString("country_code"), rs.getString("currency_code"), rs.getString("status")));
    }

    @Transactional
    public ClientDto createClient(CreateClientRequest request, String actor) {
        String code = code(request.code(), "code client");
        required(request.legalName(), "raison sociale");
        String country = code(request.countryCode(), "pays");
        jdbc.update("""
                INSERT INTO deployment_client(code,legal_name,commercial_name,country_code,currency_code,status,created_by)
                VALUES (?,?,?,?,?,'DRAFT',?)
                """, code, request.legalName().trim(), trim(request.commercialName()), country,
                upper(request.currencyCode()), actor);
        return jdbc.queryForObject("""
                SELECT id,code,legal_name,commercial_name,country_code,currency_code,status
                  FROM deployment_client WHERE code=?
                """, (rs, row) -> new ClientDto(rs.getLong("id"), rs.getString("code"),
                rs.getString("legal_name"), rs.getString("commercial_name"),
                rs.getString("country_code"), rs.getString("currency_code"), rs.getString("status")), code);
    }

    public List<EnvironmentDto> environments(Long clientId) {
        return jdbc.query("SELECT * FROM deployment_environment WHERE client_id=? ORDER BY code",
                (rs, row) -> environment(rs), clientId);
    }

    @Transactional
    public EnvironmentDto createEnvironment(CreateEnvironmentRequest request, String actor) {
        validateEnvironment(request);
        String members = json(request.memberModules() == null ? List.of() : request.memberModules());
        String simulators = json(request.simulatorModules() == null ? List.of() : request.simulatorModules());
        String references = json(request.variableReferences() == null ? Map.of() : request.variableReferences());
        jdbc.update("""
                INSERT INTO deployment_environment(
                  client_id,code,environment_type,target_os,shell_type,shell_executable,
                  deployment_root,java_executable,database_type,database_host,database_port,
                  database_name,database_schema,database_user,database_password_secret_ref,
                  oracle_service_name,oracle_sid,member_modules,simulator_modules,variable_references,
                  members_bundle_path,simulators_bundle_path,license_path,license_public_key_path,created_by)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,CAST(? AS jsonb),CAST(? AS jsonb),CAST(? AS jsonb),?,?,?,?,?)
                """, request.clientId(), code(request.code(), "code environnement"),
                code(request.environmentType(), "type environnement"), request.targetOs(), request.shellType(),
                trim(request.shellExecutable()), required(request.deploymentRoot(), "répertoire"),
                request.javaExecutable() == null || request.javaExecutable().isBlank() ? "java" : request.javaExecutable().trim(),
                request.databaseType(), trim(request.databaseHost()), request.databasePort(), trim(request.databaseName()),
                trim(request.databaseSchema()), trim(request.databaseUser()), trim(request.databasePasswordSecretRef()),
                trim(request.oracleServiceName()), trim(request.oracleSid()), members, simulators, references,
                trim(request.membersBundlePath()), trim(request.simulatorsBundlePath()), trim(request.licensePath()),
                trim(request.licensePublicKeyPath()), actor);
        return jdbc.queryForObject("SELECT * FROM deployment_environment WHERE client_id=? AND code=?",
                (rs, row) -> environment(rs), request.clientId(), code(request.code(), "code environnement"));
    }

    @Transactional
    public PreflightDto preflight(Long environmentId, String actor) {
        EnvironmentDto environment = jdbc.queryForObject("SELECT * FROM deployment_environment WHERE id=?",
                (rs, row) -> environment(rs), environmentId);
        ClientDto client = jdbc.queryForObject("""
                SELECT c.id,c.code,c.legal_name,c.commercial_name,c.country_code,c.currency_code,c.status
                  FROM deployment_client c JOIN deployment_environment e ON e.client_id=c.id WHERE e.id=?
                """, (rs, row) -> new ClientDto(rs.getLong("id"), rs.getString("code"),
                rs.getString("legal_name"), rs.getString("commercial_name"),
                rs.getString("country_code"), rs.getString("currency_code"), rs.getString("status")), environmentId);
        ValidationReport report = validator.validate(manifest(client, environment));
        String verdict = report.hasBlocking() ? "BLOCKING"
                : report.checks().stream().anyMatch(check -> check.status() == CheckStatus.WARNING) ? "WARNING" : "READY";
        jdbc.update("""
                INSERT INTO deployment_preflight_report(id,environment_id,requested_by,checked_at,verdict,report_json)
                VALUES (CAST(? AS uuid),?,?,?, ?,CAST(? AS jsonb))
                """, report.executionId(), environmentId, actor, Timestamp.from(report.checkedAt()), verdict, json(report));
        return new PreflightDto(report.executionId(), report.checkedAt(), verdict, report.checks());
    }

    public List<LicenseDto> deploymentLicenses(Long environmentId) {
        String sql = "SELECT * FROM deployment_license" + (environmentId == null ? "" : " WHERE environment_id=?")
                + " ORDER BY created_at DESC";
        return environmentId == null ? jdbc.query(sql, (rs, row) -> license(rs))
                : jdbc.query(sql, (rs, row) -> license(rs), environmentId);
    }

    @Transactional
    public LicenseDto createLicense(CreateLicenseRequest request, String actor) {
        if (request.environmentId() == null) throw new IllegalArgumentException("Environnement obligatoire");
        if (request.validFrom() == null || request.validUntil() == null
                || !request.validUntil().isAfter(request.validFrom())) {
            throw new IllegalArgumentException("Période de validité de licence invalide");
        }
        required(request.bundleVersion(), "version des bundles");
        EnvironmentDto environment = environment(request.environmentId());
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO deployment_license(id,client_id,environment_id,status,valid_from,valid_until,
                  member_modules,simulator_modules,bundle_version,prepared_by)
                VALUES (?,?,?,'PENDING',?,?,CAST(? AS jsonb),CAST(? AS jsonb),?,?)
                """, id, environment.clientId(), environment.id(), request.validFrom(), request.validUntil(),
                json(environment.memberModules()), json(environment.simulatorModules()),
                request.bundleVersion().trim(), actor);
        return license(id);
    }

    public LicenseDto approveLicense(UUID id, String actor) {
        LicenseDto draft = license(id);
        if (!"PENDING".equals(draft.status())) throw new IllegalArgumentException("Licence non approuvable");
        if (actor.equalsIgnoreCase(draft.preparedBy())) throw new IllegalArgumentException("Le maker ne peut pas approuver sa propre licence");
        String privateKeyValue = property("deployment.license.private-key-path");
        String publicKeyValue = property("deployment.license.public-key-path");
        String outputRootValue = property("deployment.license.output-root");
        Path privateKey = Path.of(privateKeyValue).toAbsolutePath().normalize();
        Path publicKey = Path.of(publicKeyValue).toAbsolutePath().normalize();
        if (!Files.isRegularFile(privateKey)) throw new IllegalStateException("Clé privée de signature indisponible");
        if (!Files.isRegularFile(publicKey)) throw new IllegalStateException("Clé publique de licence indisponible");
        try {
            EnvironmentDto environment = environment(draft.environmentId());
            ClientDto client = client(draft.clientId());
            Path directory = Path.of(outputRootValue).toAbsolutePath().normalize()
                    .resolve(client.code()).resolve(environment.code()).resolve(id.toString());
            Path technical = directory.resolve("license.json.sig");
            Path pdf = directory.resolve("license.pdf");
            TechnicalLicense technicalLicense = new TechnicalLicense(id.toString(), client.code(), client.legalName(),
                    environment.code(), Instant.now().toString(), draft.validFrom().toString(),
                    draft.validUntil().toString(), draft.memberModules(), draft.simulatorModules(),
                    draft.bundleVersion(), actor, "LOCAL".equals(environment.environmentType()));
            licenses.issue(technicalLicense, licenses.readPrivateKey(privateKey), technical);
            new LicensePdfService().generate(technicalLicense, technical, pdf);
            String sha256 = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(Files.readAllBytes(technical))).toUpperCase(Locale.ROOT);
            jdbc.update("""
                    UPDATE deployment_license SET status='ACTIVE',technical_license_path=?,pdf_path=?,
                      technical_sha256=?,approved_by=?,approved_at=now() WHERE id=? AND status='PENDING'
                    """, technical.toString(), pdf.toString(), sha256, actor, id);
            jdbc.update("UPDATE deployment_environment SET license_path=?,license_public_key_path=?,updated_at=now() WHERE id=?",
                    technical.toString(), publicKey.toString(), draft.environmentId());
            return license(id);
        } catch (Exception exception) {
            throw new IllegalStateException("Échec de génération de la licence: " + exception.getMessage(), exception);
        }
    }

    public List<ExecutionDto> executions(Long environmentId) {
        String sql = "SELECT * FROM deployment_execution" + (environmentId == null ? "" : " WHERE environment_id=?")
                + " ORDER BY created_at DESC";
        return environmentId == null ? jdbc.query(sql, (rs, row) -> execution(rs))
                : jdbc.query(sql, (rs, row) -> execution(rs), environmentId);
    }

    @Transactional
    public ExecutionDto createExecution(CreateExecutionRequest request, String actor) {
        if (request.environmentId() == null) throw new IllegalArgumentException("Environnement obligatoire");
        environment(request.environmentId());
        String action = code(request.action(), "action");
        if (!Set.of("VALIDATE", "PLAN", "INSTALL", "START", "STATUS", "STOP", "UPGRADE", "ROLLBACK", "LOGS").contains(action)) {
            throw new IllegalArgumentException("Action de déploiement inconnue");
        }
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO deployment_execution(id,environment_id,action,status,requested_by,detail_json)
                VALUES (?,?,?,'PENDING_APPROVAL',?,CAST('{}' AS jsonb))
                """, id, request.environmentId(), action, actor);
        return execution(id);
    }

    public ExecutionDto approveExecution(UUID id, String actor) {
        ExecutionDto request = execution(id);
        if (!"PENDING_APPROVAL".equals(request.status())) throw new IllegalArgumentException("Exécution non approuvable");
        if (actor.equalsIgnoreCase(request.requestedBy())) throw new IllegalArgumentException("Le maker ne peut pas approuver sa propre exécution");
        jdbc.update("UPDATE deployment_execution SET status='RUNNING',approved_by=?,started_at=now() WHERE id=? AND status='PENDING_APPROVAL'", actor, id);
        try {
            EnvironmentDto environment = environment(request.environmentId());
            ClientDto client = client(environment.clientId());
            DeploymentManifest manifest = manifest(client, environment);
            Map<String,Object> detail = execute(request.action(), id, manifest);
            jdbc.update("UPDATE deployment_execution SET status='SUCCESS',finished_at=now(),detail_json=CAST(? AS jsonb) WHERE id=?",
                    json(detail), id);
        } catch (Exception exception) {
            jdbc.update("UPDATE deployment_execution SET status='FAILED',finished_at=now(),detail_json=CAST(? AS jsonb) WHERE id=?",
                    json(Map.of("error", exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage())), id);
        }
        return execution(id);
    }

    private Map<String,Object> execute(String action, UUID id, DeploymentManifest manifest) throws Exception {
        if (Set.of("VALIDATE", "PLAN", "INSTALL", "START", "UPGRADE").contains(action)) {
            ValidationReport report = validator.validate(manifest);
            if (report.hasBlocking()) {
                if ("VALIDATE".equals(action)) return Map.of("verdict", "BLOCKING", "checks", report.checks());
                throw new IllegalStateException("Préflight BLOQUANT: exécution interdite");
            }
            if ("VALIDATE".equals(action)) return Map.of("verdict", "READY", "checks", report.checks());
        }
        return switch (action) {
            case "PLAN" -> Map.of("actions", engine.plan(manifest).actions());
            case "INSTALL", "UPGRADE" -> {
                Path staged = manifest.deploymentRoot().resolve("state/deployment-request-" + id + ".yml");
                new ManifestWriter().write(manifest, staged);
                if ("INSTALL".equals(action)) engine.install(manifest, staged); else engine.upgrade(manifest, staged);
                yield Map.of("manifest", staged.toString(), "result", "COMPLETED");
            }
            case "START" -> Map.of("processes", engine.start(manifest));
            case "STATUS" -> Map.of("bundles", engine.status(manifest));
            case "STOP" -> Map.of("bundles", engine.stop(manifest));
            case "ROLLBACK" -> Map.of("restoredBackup", engine.rollback(manifest).toString());
            case "LOGS" -> Map.of("members", engine.logs(manifest, "members", 100),
                    "simulators", engine.logs(manifest, "simulators", 100));
            default -> throw new IllegalArgumentException("Action inconnue");
        };
    }

    private DeploymentManifest manifest(ClientDto client, EnvironmentDto environment) {
        return new DeploymentManifest("1", client.code(), client.legalName(), environment.code(),
                TargetOs.valueOf(environment.targetOs()), ShellType.valueOf(environment.shellType()),
                environment.shellExecutable(), path(environment.deploymentRoot()), environment.javaExecutable(),
                new DatabaseConfig(DatabaseType.valueOf(environment.databaseType()), environment.databaseHost(),
                        environment.databasePort(), environment.databaseName(), environment.databaseSchema(),
                        environment.databaseUser(), environment.databasePasswordSecretRef(),
                        environment.oracleServiceName(), environment.oracleSid()),
                environment.memberModules(), environment.simulatorModules(), environment.variableReferences(),
                path(environment.membersBundlePath()), path(environment.simulatorsBundlePath()),
                path(environment.licensePath()), path(environment.licensePublicKeyPath()));
    }

    private void validateEnvironment(CreateEnvironmentRequest request) {
        if (request.clientId() == null) throw new IllegalArgumentException("Client obligatoire");
        TargetOs os = TargetOs.valueOf(required(request.targetOs(), "OS cible"));
        ShellType shell = ShellType.valueOf(required(request.shellType(), "shell"));
        if (!shell.supports(os)) throw new IllegalArgumentException("Shell incompatible avec l'OS cible");
        DatabaseType.valueOf(required(request.databaseType(), "type de base"));
        validateModules(request.memberModules(), ModuleSide.MEMBER);
        validateModules(request.simulatorModules(), ModuleSide.SIMULATOR);
        Map<String,String> references = request.variableReferences() == null ? Map.of() : request.variableReferences();
        references.forEach((name, value) -> {
            String upper = name.toUpperCase(Locale.ROOT);
            if (SENSITIVE_MARKERS.stream().anyMatch(upper::contains)
                    && (value == null || !value.startsWith("secret://"))) {
                throw new IllegalArgumentException("La variable sensible " + name + " doit utiliser une référence secret://");
            }
        });
        if (request.databaseType() != null && !"NONE".equals(request.databaseType())
                && (request.databasePasswordSecretRef() == null
                || !request.databasePasswordSecretRef().startsWith("secret://"))) {
            throw new IllegalArgumentException("Le mot de passe DB doit utiliser une référence secret://");
        }
    }

    private void validateModules(List<String> modules, ModuleSide side) {
        if (modules == null) return;
        modules.forEach(code -> {
            var module = catalog.find(code).orElseThrow(() -> new IllegalArgumentException("Module inconnu: " + code));
            if (module.side() != side) throw new IllegalArgumentException("Module classé du mauvais côté: " + code);
        });
    }

    private EnvironmentDto environment(ResultSet rs) throws SQLException {
        return new EnvironmentDto(rs.getLong("id"), rs.getLong("client_id"), rs.getString("code"),
                rs.getString("environment_type"), rs.getString("target_os"), rs.getString("shell_type"),
                rs.getString("shell_executable"), rs.getString("deployment_root"), rs.getString("java_executable"),
                rs.getString("database_type"), rs.getString("database_host"), (Integer) rs.getObject("database_port"),
                rs.getString("database_name"), rs.getString("database_schema"), rs.getString("database_user"),
                rs.getString("database_password_secret_ref"), rs.getString("oracle_service_name"), rs.getString("oracle_sid"),
                read(rs.getString("member_modules"), new TypeReference<List<String>>() {}),
                read(rs.getString("simulator_modules"), new TypeReference<List<String>>() {}),
                read(rs.getString("variable_references"), new TypeReference<Map<String,String>>() {}),
                rs.getString("members_bundle_path"), rs.getString("simulators_bundle_path"),
                rs.getString("license_path"), rs.getString("license_public_key_path"));
    }

    private EnvironmentDto environment(Long id) {
        return jdbc.queryForObject("SELECT * FROM deployment_environment WHERE id=?", (rs, row) -> environment(rs), id);
    }

    private ClientDto client(Long id) {
        return jdbc.queryForObject("""
                SELECT id,code,legal_name,commercial_name,country_code,currency_code,status
                  FROM deployment_client WHERE id=?
                """, (rs, row) -> new ClientDto(rs.getLong("id"), rs.getString("code"),
                rs.getString("legal_name"), rs.getString("commercial_name"), rs.getString("country_code"),
                rs.getString("currency_code"), rs.getString("status")), id);
    }

    private LicenseDto license(UUID id) {
        return jdbc.queryForObject("SELECT * FROM deployment_license WHERE id=?", (rs, row) -> license(rs), id);
    }

    private LicenseDto license(ResultSet rs) throws SQLException {
        return new LicenseDto(rs.getObject("id", UUID.class), rs.getLong("client_id"), rs.getLong("environment_id"),
                rs.getString("status"), rs.getDate("valid_from").toLocalDate(), rs.getDate("valid_until").toLocalDate(),
                read(rs.getString("member_modules"), new TypeReference<List<String>>() {}),
                read(rs.getString("simulator_modules"), new TypeReference<List<String>>() {}),
                rs.getString("bundle_version"), rs.getString("technical_license_path"), rs.getString("pdf_path"),
                rs.getString("technical_sha256"), rs.getString("prepared_by"), rs.getString("approved_by"),
                instant(rs.getTimestamp("created_at")), instant(rs.getTimestamp("approved_at")));
    }

    private ExecutionDto execution(UUID id) {
        return jdbc.queryForObject("SELECT * FROM deployment_execution WHERE id=?", (rs, row) -> execution(rs), id);
    }

    private ExecutionDto execution(ResultSet rs) throws SQLException {
        return new ExecutionDto(rs.getObject("id", UUID.class), rs.getLong("environment_id"), rs.getString("action"),
                rs.getString("status"), rs.getString("requested_by"), rs.getString("approved_by"),
                instant(rs.getTimestamp("started_at")), instant(rs.getTimestamp("finished_at")),
                read(rs.getString("detail_json"), new TypeReference<Map<String,Object>>() {}),
                instant(rs.getTimestamp("created_at")));
    }

    private String property(String name) {
        String value = springEnvironment.getProperty(name);
        if (value == null || value.isBlank()) throw new IllegalStateException("Configuration serveur absente: " + name);
        return value.trim();
    }

    private static Instant instant(Timestamp value) { return value == null ? null : value.toInstant(); }

    private <T> T read(String json, TypeReference<T> type) {
        try { return mapper.readValue(json, type); }
        catch (Exception exception) { throw new IllegalStateException("Configuration JSON de déploiement invalide", exception); }
    }

    private String json(Object value) {
        try { return mapper.writeValueAsString(value == null ? Map.of() : value); }
        catch (Exception exception) { throw new IllegalArgumentException("Valeur non sérialisable", exception); }
    }

    private static String code(String value, String label) {
        String normalized = required(value, label).toUpperCase(Locale.ROOT);
        if (!normalized.matches("[A-Z0-9][A-Z0-9_-]{1,79}")) throw new IllegalArgumentException(label + " invalide");
        return normalized;
    }

    private static String upper(String value) { return value == null ? null : value.trim().toUpperCase(Locale.ROOT); }
    private static String trim(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private static String required(String value, String label) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + " obligatoire");
        return value.trim();
    }
    private static Path path(String value) { return value == null || value.isBlank() ? null : Path.of(value).toAbsolutePath().normalize(); }
}
