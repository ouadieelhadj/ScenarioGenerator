-- Administration des déploiements, clients banques, environnements et audits.
-- Migration additive et rejouable, à appliquer après 19_frontend_global_catalog.sql.

INSERT INTO permissions(code,label,category) VALUES
 ('DEPLOYMENT_VIEW','Consulter les déploiements','DEPLOYMENT'),
 ('DEPLOYMENT_PREPARE','Préparer un déploiement','DEPLOYMENT'),
 ('DEPLOYMENT_APPROVE','Approuver un déploiement','DEPLOYMENT'),
 ('DEPLOYMENT_EXECUTE','Exécuter un déploiement','DEPLOYMENT'),
 ('DEPLOYMENT_ROLLBACK','Restaurer une version','DEPLOYMENT')
ON CONFLICT (code) DO UPDATE SET label=EXCLUDED.label,category=EXCLUDED.category;

INSERT INTO role_permissions(role_id,permission_id)
SELECT r.id,p.id FROM roles r CROSS JOIN permissions p
WHERE r.code='ADMIN' AND p.code LIKE 'DEPLOYMENT_%'
ON CONFLICT DO NOTHING;

CREATE TABLE IF NOT EXISTS deployment_client (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(80) NOT NULL UNIQUE,
    legal_name VARCHAR(200) NOT NULL,
    commercial_name VARCHAR(200),
    country_code VARCHAR(3) NOT NULL,
    currency_code VARCHAR(3),
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT'
        CHECK (status IN ('DRAFT','ACTIVE','SUSPENDED','EXPIRED')),
    created_by VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS deployment_environment (
    id BIGSERIAL PRIMARY KEY,
    client_id BIGINT NOT NULL REFERENCES deployment_client(id) ON DELETE CASCADE,
    code VARCHAR(80) NOT NULL,
    environment_type VARCHAR(20) NOT NULL
        CHECK (environment_type IN ('LOCAL','DEV','TEST','RECETTE','PREPROD','PROD')),
    target_os VARCHAR(20) NOT NULL CHECK (target_os IN ('WINDOWS','LINUX')),
    shell_type VARCHAR(30) NOT NULL
        CHECK (shell_type IN ('GIT_BASH','POWERSHELL','CMD_WINDOWS','BASH_LINUX')),
    shell_executable VARCHAR(500),
    deployment_root VARCHAR(1000) NOT NULL,
    java_executable VARCHAR(500) NOT NULL DEFAULT 'java',
    database_type VARCHAR(20) NOT NULL DEFAULT 'NONE'
        CHECK (database_type IN ('NONE','POSTGRESQL','ORACLE')),
    database_host VARCHAR(255),
    database_port INTEGER,
    database_name VARCHAR(255),
    database_schema VARCHAR(255),
    database_user VARCHAR(255),
    database_password_secret_ref VARCHAR(500),
    oracle_service_name VARCHAR(255),
    oracle_sid VARCHAR(255),
    member_modules JSONB NOT NULL DEFAULT '[]'::jsonb,
    simulator_modules JSONB NOT NULL DEFAULT '[]'::jsonb,
    variable_references JSONB NOT NULL DEFAULT '{}'::jsonb,
    members_bundle_path VARCHAR(1000),
    simulators_bundle_path VARCHAR(1000),
    license_path VARCHAR(1000),
    license_public_key_path VARCHAR(1000),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_by VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    UNIQUE(client_id,code)
);

CREATE TABLE IF NOT EXISTS deployment_preflight_report (
    id UUID PRIMARY KEY,
    environment_id BIGINT NOT NULL REFERENCES deployment_environment(id) ON DELETE CASCADE,
    requested_by VARCHAR(100) NOT NULL,
    checked_at TIMESTAMP NOT NULL,
    verdict VARCHAR(20) NOT NULL CHECK (verdict IN ('READY','WARNING','BLOCKING')),
    report_json JSONB NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS deployment_license (
    id UUID PRIMARY KEY,
    client_id BIGINT NOT NULL REFERENCES deployment_client(id),
    environment_id BIGINT NOT NULL REFERENCES deployment_environment(id),
    status VARCHAR(20) NOT NULL CHECK (status IN ('DRAFT','PENDING','ACTIVE','REJECTED','REVOKED','EXPIRED')),
    valid_from DATE NOT NULL,
    valid_until DATE NOT NULL,
    member_modules JSONB NOT NULL,
    simulator_modules JSONB NOT NULL,
    bundle_version VARCHAR(100) NOT NULL,
    technical_license_path VARCHAR(1000),
    pdf_path VARCHAR(1000),
    technical_sha256 VARCHAR(64),
    prepared_by VARCHAR(100) NOT NULL,
    approved_by VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    approved_at TIMESTAMP,
    CHECK (approved_by IS NULL OR approved_by <> prepared_by)
);

CREATE TABLE IF NOT EXISTS deployment_execution (
    id UUID PRIMARY KEY,
    environment_id BIGINT NOT NULL REFERENCES deployment_environment(id),
    action VARCHAR(30) NOT NULL
        CHECK (action IN ('VALIDATE','PLAN','INSTALL','START','STATUS','STOP','UPGRADE','ROLLBACK','LOGS')),
    status VARCHAR(30) NOT NULL,
    requested_by VARCHAR(100) NOT NULL,
    approved_by VARCHAR(100),
    started_at TIMESTAMP,
    finished_at TIMESTAMP,
    detail_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    CHECK (approved_by IS NULL OR approved_by <> requested_by)
);

-- Rejouabilité si une version antérieure de cette migration avait créé la
-- contrainte sans l'action STATUS.
ALTER TABLE deployment_execution DROP CONSTRAINT IF EXISTS deployment_execution_action_check;
ALTER TABLE deployment_execution ADD CONSTRAINT deployment_execution_action_check
    CHECK (action IN ('VALIDATE','PLAN','INSTALL','START','STATUS','STOP','UPGRADE','ROLLBACK','LOGS'));

GRANT SELECT,INSERT,UPDATE,DELETE ON deployment_client,deployment_environment,
 deployment_preflight_report,deployment_license,deployment_execution TO scenario_user;
GRANT USAGE,SELECT ON ALL SEQUENCES IN SCHEMA public TO scenario_user;
