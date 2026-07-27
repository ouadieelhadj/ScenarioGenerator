-- Portail modulaire, RBAC dynamique, équipes, Maker/Checker et SLA.
-- Migration additive et rejouable. Le champ users.role reste disponible
-- pendant la période de compatibilité.

CREATE TABLE IF NOT EXISTS app_module (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(80) NOT NULL UNIQUE,
    label_key VARCHAR(160) NOT NULL,
    icon VARCHAR(80),
    display_order INTEGER NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    config_json JSONB NOT NULL DEFAULT '{}'::jsonb
);

CREATE TABLE IF NOT EXISTS screen_definition (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(100) NOT NULL UNIQUE,
    route_template VARCHAR(255) NOT NULL,
    component_key VARCHAR(100) NOT NULL,
    shared_screen BOOLEAN NOT NULL DEFAULT FALSE,
    active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS navigation_node (
    id BIGSERIAL PRIMARY KEY,
    module_id BIGINT NOT NULL REFERENCES app_module(id),
    parent_id BIGINT REFERENCES navigation_node(id),
    node_type VARCHAR(20) NOT NULL CHECK (node_type IN ('MENU','SUBMENU','SCREEN')),
    code VARCHAR(100) NOT NULL,
    label_key VARCHAR(160) NOT NULL,
    icon VARCHAR(80),
    screen_definition_id BIGINT REFERENCES screen_definition(id),
    display_order INTEGER NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    context_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    UNIQUE(module_id, code),
    CHECK ((node_type = 'SCREEN' AND screen_definition_id IS NOT NULL)
        OR (node_type <> 'SCREEN' AND screen_definition_id IS NULL))
);

CREATE TABLE IF NOT EXISTS user_profiles (
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role_id BIGINT NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    assigned_at TIMESTAMP NOT NULL DEFAULT now(),
    assigned_by VARCHAR(50),
    PRIMARY KEY(user_id, role_id)
);

INSERT INTO user_profiles(user_id, role_id, assigned_by)
SELECT u.id, r.id, 'MIGRATION_18'
FROM users u JOIN roles r ON r.code = u.role
ON CONFLICT DO NOTHING;

CREATE TABLE IF NOT EXISTS profile_navigation_grant (
    role_id BIGINT NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    navigation_node_id BIGINT NOT NULL REFERENCES navigation_node(id) ON DELETE CASCADE,
    allowed BOOLEAN NOT NULL DEFAULT TRUE,
    PRIMARY KEY(role_id, navigation_node_id)
);

CREATE TABLE IF NOT EXISTS user_navigation_override (
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    navigation_node_id BIGINT NOT NULL REFERENCES navigation_node(id) ON DELETE CASCADE,
    allowed BOOLEAN NOT NULL,
    reason VARCHAR(500),
    valid_from TIMESTAMP,
    valid_until TIMESTAMP,
    PRIMARY KEY(user_id, navigation_node_id)
);

CREATE TABLE IF NOT EXISTS screen_action (
    id BIGSERIAL PRIMARY KEY,
    screen_definition_id BIGINT NOT NULL REFERENCES screen_definition(id) ON DELETE CASCADE,
    code VARCHAR(100) NOT NULL,
    label_key VARCHAR(160) NOT NULL,
    sensitive BOOLEAN NOT NULL DEFAULT FALSE,
    UNIQUE(screen_definition_id, code)
);

CREATE TABLE IF NOT EXISTS profile_action_grant (
    role_id BIGINT NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    screen_action_id BIGINT NOT NULL REFERENCES screen_action(id) ON DELETE CASCADE,
    allowed BOOLEAN NOT NULL DEFAULT TRUE,
    PRIMARY KEY(role_id, screen_action_id)
);

CREATE TABLE IF NOT EXISTS user_action_override (
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    screen_action_id BIGINT NOT NULL REFERENCES screen_action(id) ON DELETE CASCADE,
    allowed BOOLEAN NOT NULL,
    reason VARCHAR(500),
    PRIMARY KEY(user_id, screen_action_id)
);

CREATE TABLE IF NOT EXISTS business_team (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(100) NOT NULL UNIQUE,
    label VARCHAR(160) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS business_team_membership (
    id BIGSERIAL PRIMARY KEY,
    team_id BIGINT NOT NULL REFERENCES business_team(id),
    user_id BIGINT NOT NULL REFERENCES users(id),
    module_code VARCHAR(80) NOT NULL,
    operation_code VARCHAR(100) NOT NULL,
    team_role VARCHAR(20) NOT NULL CHECK (team_role IN ('MAKER','CHECKER','SUPERVISOR')),
    valid_from TIMESTAMP NOT NULL DEFAULT now(),
    valid_until TIMESTAMP,
    active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE INDEX IF NOT EXISTS idx_team_membership_scope
    ON business_team_membership(user_id, module_code, operation_code, active);

CREATE TABLE IF NOT EXISTS maker_checker_assignment (
    id BIGSERIAL PRIMARY KEY,
    team_id BIGINT NOT NULL REFERENCES business_team(id),
    module_code VARCHAR(80) NOT NULL,
    screen_code VARCHAR(100) NOT NULL,
    operation_code VARCHAR(100) NOT NULL,
    maker_user_id BIGINT NOT NULL REFERENCES users(id),
    checker_user_id BIGINT NOT NULL REFERENCES users(id),
    substitute_checker_user_id BIGINT REFERENCES users(id),
    valid_from TIMESTAMP NOT NULL DEFAULT now(),
    valid_until TIMESTAMP,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    CHECK (maker_user_id <> checker_user_id),
    CHECK (substitute_checker_user_id IS NULL OR maker_user_id <> substitute_checker_user_id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_active_maker_checker_assignment
    ON maker_checker_assignment(module_code, screen_code, operation_code, maker_user_id)
    WHERE active;

CREATE TABLE IF NOT EXISTS business_calendar (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    label VARCHAR(160) NOT NULL,
    timezone VARCHAR(80) NOT NULL DEFAULT 'Africa/Casablanca',
    cutoff_time TIME NOT NULL DEFAULT '17:00',
    active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS business_calendar_day (
    id BIGSERIAL PRIMARY KEY,
    calendar_id BIGINT NOT NULL REFERENCES business_calendar(id) ON DELETE CASCADE,
    business_date DATE NOT NULL,
    working_day BOOLEAN NOT NULL,
    opening_time TIME,
    closing_time TIME,
    reason VARCHAR(255),
    UNIQUE(calendar_id, business_date)
);

CREATE TABLE IF NOT EXISTS sla_policy (
    id BIGSERIAL PRIMARY KEY,
    module_code VARCHAR(80) NOT NULL,
    screen_code VARCHAR(100) NOT NULL,
    operation_code VARCHAR(100) NOT NULL,
    calendar_id BIGINT NOT NULL REFERENCES business_calendar(id),
    maker_minutes INTEGER NOT NULL CHECK (maker_minutes >= 0),
    checker_minutes INTEGER NOT NULL CHECK (checker_minutes >= 0),
    technical_minutes INTEGER NOT NULL CHECK (technical_minutes >= 0),
    global_minutes INTEGER NOT NULL CHECK (global_minutes >= 0),
    warning_minutes INTEGER NOT NULL DEFAULT 0 CHECK (warning_minutes >= 0),
    active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS workflow_policy (
    id BIGSERIAL PRIMARY KEY,
    module_code VARCHAR(80) NOT NULL,
    screen_code VARCHAR(100) NOT NULL,
    operation_code VARCHAR(100) NOT NULL,
    maker_checker_required BOOLEAN NOT NULL DEFAULT FALSE,
    execution_mode VARCHAR(30) NOT NULL DEFAULT 'DIRECT'
        CHECK (execution_mode IN ('DIRECT','BATCH','API_IMMEDIATE','MANUAL')),
    executor_code VARCHAR(100),
    required_approvals INTEGER NOT NULL DEFAULT 1 CHECK (required_approvals > 0),
    sla_policy_id BIGINT REFERENCES sla_policy(id),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    UNIQUE(module_code, screen_code, operation_code)
);

CREATE TABLE IF NOT EXISTS workflow_request (
    id BIGSERIAL PRIMARY KEY,
    public_id UUID NOT NULL UNIQUE,
    module_code VARCHAR(80) NOT NULL,
    screen_code VARCHAR(100) NOT NULL,
    operation_code VARCHAR(100) NOT NULL,
    business_object_type VARCHAR(100) NOT NULL,
    business_object_id VARCHAR(160) NOT NULL,
    maker_user_id BIGINT NOT NULL REFERENCES users(id),
    checker_user_id BIGINT REFERENCES users(id),
    workflow_status VARCHAR(40) NOT NULL,
    execution_status VARCHAR(40),
    business_status VARCHAR(40),
    deadline_status VARCHAR(20) NOT NULL DEFAULT 'ON_TIME',
    payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    payload_hash VARCHAR(128),
    idempotency_key VARCHAR(160) NOT NULL UNIQUE,
    maker_due_at TIMESTAMP,
    checker_due_at TIMESTAMP,
    technical_due_at TIMESTAMP,
    global_due_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    submitted_at TIMESTAMP,
    decided_at TIMESTAMP,
    completed_at TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    UNIQUE(module_code, operation_code, business_object_type, business_object_id, workflow_status)
);

CREATE INDEX IF NOT EXISTS idx_workflow_maker
    ON workflow_request(maker_user_id, workflow_status, global_due_at);
CREATE INDEX IF NOT EXISTS idx_workflow_checker
    ON workflow_request(checker_user_id, workflow_status, checker_due_at);

CREATE TABLE IF NOT EXISTS workflow_event (
    id BIGSERIAL PRIMARY KEY,
    workflow_request_id BIGINT NOT NULL REFERENCES workflow_request(id) ON DELETE CASCADE,
    event_type VARCHAR(80) NOT NULL,
    actor_user_id BIGINT REFERENCES users(id),
    reason VARCHAR(1000),
    event_data JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS workflow_outbox (
    id BIGSERIAL PRIMARY KEY,
    workflow_request_id BIGINT NOT NULL REFERENCES workflow_request(id),
    event_type VARCHAR(80) NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP NOT NULL DEFAULT now(),
    last_error VARCHAR(2000),
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    processed_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS notification_event (
    id BIGSERIAL PRIMARY KEY,
    workflow_request_id BIGINT REFERENCES workflow_request(id),
    event_type VARCHAR(80) NOT NULL,
    recipient_user_id BIGINT NOT NULL REFERENCES users(id),
    copy_user_id BIGINT REFERENCES users(id),
    channel VARCHAR(20) NOT NULL CHECK (channel IN ('IN_APP','EMAIL')),
    deduplication_key VARCHAR(200) NOT NULL UNIQUE,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    sent_at TIMESTAMP,
    last_error VARCHAR(2000)
);

CREATE TABLE IF NOT EXISTS security_audit_event (
    id BIGSERIAL PRIMARY KEY,
    actor_login VARCHAR(50) NOT NULL,
    action_code VARCHAR(100) NOT NULL,
    module_code VARCHAR(80),
    object_type VARCHAR(100),
    object_id VARCHAR(160),
    reason VARCHAR(1000),
    before_data JSONB,
    after_data JSONB,
    correlation_id VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

-- Catalogue initial. Les écrans métier sont activés progressivement.
INSERT INTO app_module(code,label_key,icon,display_order) VALUES
 ('CORE','menu.core','pi pi-home',10),
 ('DMAS_MASTERCARD','menu.dmas','pi pi-credit-card',20),
 ('SWAM_LIS_MEMBER','menu.swamLisMember','pi pi-building',30),
 ('SWAM_LIS_SWITCH','menu.swamLisSwitch','pi pi-sitemap',40)
ON CONFLICT (code) DO NOTHING;

INSERT INTO screen_definition(code,route_template,component_key,shared_screen) VALUES
 ('DASHBOARD','/dashboard','DASHBOARD',TRUE),
 ('DMAS','/dmas','DMAS',FALSE),
 ('ADMIN_USERS','/admin','ADMIN_USERS',FALSE),
 ('CLEARING_TRANSACTIONS','/modules/:moduleCode/transactions','CLEARING_TRANSACTIONS',TRUE),
 ('CLEARING_FILES','/modules/:moduleCode/files','CLEARING_FILES',TRUE),
 ('CLEARING_RECONCILIATION','/modules/:moduleCode/reconciliation','CLEARING_RECONCILIATION',TRUE),
 ('CLEARING_CHARGEBACKS','/modules/:moduleCode/chargebacks','CLEARING_CHARGEBACKS',TRUE),
 ('CLEARING_EOD','/modules/:moduleCode/eod','CLEARING_EOD',TRUE),
 ('CLEARING_ACCOUNTING','/modules/:moduleCode/accounting','CLEARING_ACCOUNTING',TRUE)
ON CONFLICT (code) DO NOTHING;

INSERT INTO business_calendar(code,label,timezone,cutoff_time)
VALUES ('MA_BANKING','Calendrier bancaire marocain','Africa/Casablanca','17:00')
ON CONFLICT (code) DO NOTHING;

INSERT INTO navigation_node(module_id,node_type,code,label_key,icon,screen_definition_id,display_order)
SELECT m.id,'SCREEN','CORE_DASHBOARD','menu.dashboard','pi pi-home',s.id,10
FROM app_module m, screen_definition s
WHERE m.code='CORE' AND s.code='DASHBOARD'
ON CONFLICT (module_id,code) DO NOTHING;

INSERT INTO navigation_node(module_id,node_type,code,label_key,icon,screen_definition_id,display_order)
SELECT m.id,'SCREEN','DMAS_MAIN','menu.dmas','pi pi-credit-card',s.id,10
FROM app_module m, screen_definition s
WHERE m.code='DMAS_MASTERCARD' AND s.code='DMAS'
ON CONFLICT (module_id,code) DO NOTHING;

INSERT INTO navigation_node(module_id,node_type,code,label_key,icon,screen_definition_id,display_order,context_json)
SELECT m.id,'SCREEN',m.code||'_TRANSACTIONS','clearing.transactions','pi pi-list',s.id,10,
       jsonb_build_object('moduleCode',m.code)
FROM app_module m, screen_definition s
WHERE m.code IN ('SWAM_LIS_MEMBER','SWAM_LIS_SWITCH') AND s.code='CLEARING_TRANSACTIONS'
ON CONFLICT (module_id,code) DO NOTHING;

INSERT INTO navigation_node(module_id,node_type,code,label_key,icon,screen_definition_id,display_order,context_json)
SELECT m.id,'SCREEN',m.code||'_FILES','clearing.files','pi pi-file',s.id,20,
       jsonb_build_object('moduleCode',m.code)
FROM app_module m, screen_definition s
WHERE m.code IN ('SWAM_LIS_MEMBER','SWAM_LIS_SWITCH') AND s.code='CLEARING_FILES'
ON CONFLICT (module_id,code) DO NOTHING;

INSERT INTO navigation_node(module_id,node_type,code,label_key,icon,screen_definition_id,display_order,context_json)
SELECT m.id,'SCREEN',m.code||'_CHARGEBACKS','clearing.chargebacks','pi pi-exclamation-triangle',s.id,30,
       jsonb_build_object('moduleCode',m.code)
FROM app_module m, screen_definition s
WHERE m.code IN ('SWAM_LIS_MEMBER','SWAM_LIS_SWITCH') AND s.code='CLEARING_CHARGEBACKS'
ON CONFLICT (module_id,code) DO NOTHING;

-- L'administrateur existant reçoit le catalogue initial. Les autres profils sont
-- configurés depuis l'administration.
INSERT INTO profile_navigation_grant(role_id,navigation_node_id,allowed)
SELECT r.id,n.id,TRUE FROM roles r CROSS JOIN navigation_node n
WHERE r.code='ADMIN'
ON CONFLICT (role_id,navigation_node_id) DO UPDATE SET allowed=EXCLUDED.allowed;

-- L'orchestrateur utilise scenario_user. Les sauvegardes restaurées peuvent
-- réattribuer les tables existantes à postgres, les droits sont donc réaffirmés.
GRANT SELECT,INSERT,UPDATE,DELETE ON
    users,roles,permissions,role_permissions,
    app_module,screen_definition,navigation_node,user_profiles,
    profile_navigation_grant,user_navigation_override,
    screen_action,profile_action_grant,user_action_override,
    business_team,business_team_membership,maker_checker_assignment,
    business_calendar,business_calendar_day,sla_policy,workflow_policy,
    workflow_request,workflow_event,workflow_outbox,notification_event,
    security_audit_event
TO scenario_user;

GRANT USAGE,SELECT ON ALL SEQUENCES IN SCHEMA public TO scenario_user;
