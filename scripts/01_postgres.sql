-- ═══════════════════════════════════════════════════════════
-- ScenarioGenerator — PostgreSQL Script
-- ═══════════════════════════════════════════════════════════

-- Création utilisateur et base
CREATE USER scenario_user WITH PASSWORD 'postgres123';
CREATE DATABASE scenariogenerator OWNER scenario_user;
GRANT ALL PRIVILEGES ON DATABASE scenariogenerator TO scenario_user;

\connect scenariogenerator

-- Users
CREATE TABLE users (
    id          BIGSERIAL PRIMARY KEY,
    login       VARCHAR(50)  UNIQUE NOT NULL,
    password    VARCHAR(255) NOT NULL,
    email       VARCHAR(100),
    role        VARCHAR(20)  NOT NULL,
    active      BOOLEAN      DEFAULT TRUE,
    created_at  TIMESTAMP    DEFAULT NOW(),
    created_by  VARCHAR(50),
    last_login  TIMESTAMP
);

-- Message Types
CREATE TABLE message_types (
    id               BIGSERIAL PRIMARY KEY,
    code             VARCHAR(4)   NOT NULL,
    name             VARCHAR(100) NOT NULL,
    category         VARCHAR(50)  NOT NULL,
    description      VARCHAR(255),
    processing_codes TEXT,
    active           BOOLEAN   DEFAULT TRUE,
    created_at       TIMESTAMP DEFAULT NOW()
);

-- Tests
CREATE TABLE tests (
    id               BIGSERIAL PRIMARY KEY,
    name             VARCHAR(100) NOT NULL,
    description      VARCHAR(255),
    category         VARCHAR(50),
    message_type_id  BIGINT REFERENCES message_types(id),
    config           TEXT,
    expected_de039   VARCHAR(2),
    active           BOOLEAN   DEFAULT TRUE,
    created_at       TIMESTAMP DEFAULT NOW(),
    created_by       BIGINT REFERENCES users(id)
);

-- TPS Steps
CREATE TABLE tps_steps (
    id            BIGSERIAL PRIMARY KEY,
    test_id       BIGINT  NOT NULL REFERENCES tests(id) ON DELETE CASCADE,
    step_order    INTEGER NOT NULL,
    start_seconds INTEGER NOT NULL,
    end_seconds   INTEGER NOT NULL,
    tps_value     INTEGER NOT NULL
);

-- User Tests
CREATE TABLE user_tests (
    user_id     BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    test_id     BIGINT NOT NULL REFERENCES tests(id) ON DELETE CASCADE,
    assigned_at TIMESTAMP DEFAULT NOW(),
    assigned_by BIGINT REFERENCES users(id),
    PRIMARY KEY (user_id, test_id)
);

-- Executions
CREATE TABLE executions (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT       NOT NULL REFERENCES users(id),
    test_id             BIGINT       NOT NULL REFERENCES tests(id),
    mode                VARCHAR(20)  NOT NULL,
    status              VARCHAR(20)  NOT NULL,
    tps_target          INTEGER,
    duration_seconds    INTEGER,
    tx_total            INTEGER      DEFAULT 0,
    tx_sent             INTEGER      DEFAULT 0,
    tx_approved         INTEGER      DEFAULT 0,
    tx_declined         INTEGER      DEFAULT 0,
    tps_actual_avg      DECIMAL(10,2),
    response_time_avg   DECIMAL(10,2),
    response_time_min   DECIMAL(10,2),
    response_time_max   DECIMAL(10,2),
    response_time_p95   DECIMAL(10,2),
    response_time_p99   DECIMAL(10,2),
    started_at          TIMESTAMP    DEFAULT NOW(),
    ended_at            TIMESTAMP,
    report_dir          VARCHAR(255),
    report_pdf          VARCHAR(255),
    report_excel        VARCHAR(255)
);

-- Results
CREATE TABLE results (
    id              BIGSERIAL PRIMARY KEY,
    execution_id    BIGINT      NOT NULL REFERENCES executions(id) ON DELETE CASCADE,
    pan_masked      VARCHAR(20),
    de039           VARCHAR(2),
    de038_auth_code VARCHAR(6),
    approved        BOOLEAN,
    duration_ms     INTEGER,
    request_hex     TEXT,
    response_hex    TEXT,
    executed_at     TIMESTAMP DEFAULT NOW()
);

-- Index
CREATE INDEX idx_executions_user   ON executions(user_id);
CREATE INDEX idx_executions_test   ON executions(test_id);
CREATE INDEX idx_executions_status ON executions(status);
CREATE INDEX idx_results_execution ON results(execution_id);
CREATE INDEX idx_results_de039     ON results(de039);
CREATE INDEX idx_user_tests_user   ON user_tests(user_id);

-- Admin par défaut (password : Admin123!)
INSERT INTO users (login, password, email, role, active, created_by)
VALUES ('admin',
        '$2a$12$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2.uheWG/igi',
        'admin@staging.com', 'ADMIN', TRUE, 'system');

-- Types de messages
INSERT INTO message_types (code, name, category, description, processing_codes) VALUES
('0100', 'Authorization Request', 'AUTHORIZATION', 'Mastercard authorization request',
'[{"code":"000000","label":"Purchase"},{"code":"010000","label":"Cash Advance"},{"code":"200000","label":"Refund"},{"code":"310000","label":"Balance Inquiry"},{"code":"340000","label":"Mini Statement"}]'),
('0200', 'Financial Transaction Request', 'FINANCIAL', 'Financial transaction request',
'[{"code":"010000","label":"Cash Withdrawal"},{"code":"000000","label":"Purchase"}]'),
('0400', 'Reversal Request', 'REVERSAL', 'Transaction reversal request',
'[{"code":"000000","label":"Purchase Reversal"},{"code":"010000","label":"Cash Reversal"}]'),
('0800', 'Network Management Request', 'NETWORK', 'Network management message',
'[{"code":"301","label":"Sign-on"},{"code":"302","label":"Echo Test"}]'),
('0820', 'Key Exchange Request', 'NETWORK', 'Key exchange message',
'[{"code":"101","label":"ZMK Exchange"},{"code":"102","label":"ZPK Exchange"},{"code":"103","label":"ZAK Exchange"}]');

-- Tests par défaut
INSERT INTO tests (name, description, category, message_type_id, config, expected_de039, active, created_by)
VALUES
('Achat nominal CB', 'Test achat standard puce EMV', 'AUTHORIZATION',
 (SELECT id FROM message_types WHERE code = '0100'),
 '{"DE003_PROCESSING_CODE":"000000","DE004_AMOUNT":5000,"DE018_MCC":"5411","DE022_POS_ENTRY_MODE":"051","DE049_CURRENCY_CODE":"978","DE052_PIN":"1234"}',
 '00', TRUE, (SELECT id FROM users WHERE login = 'admin')),
('Retrait DAB', 'Test retrait distributeur', 'AUTHORIZATION',
 (SELECT id FROM message_types WHERE code = '0100'),
 '{"DE003_PROCESSING_CODE":"010000","DE004_AMOUNT":10000,"DE018_MCC":"6011","DE022_POS_ENTRY_MODE":"051","DE049_CURRENCY_CODE":"978","DE052_PIN":"1234"}',
 '00', TRUE, (SELECT id FROM users WHERE login = 'admin')),
('MCC bloque jeux', 'Test refus MCC jeux', 'AUTHORIZATION',
 (SELECT id FROM message_types WHERE code = '0100'),
 '{"DE003_PROCESSING_CODE":"000000","DE004_AMOUNT":5000,"DE018_MCC":"7995","DE022_POS_ENTRY_MODE":"051","DE049_CURRENCY_CODE":"978","DE052_PIN":"1234"}',
 '05', TRUE, (SELECT id FROM users WHERE login = 'admin'));

-- TPS Steps
INSERT INTO tps_steps (test_id, step_order, start_seconds, end_seconds, tps_value)
VALUES
((SELECT id FROM tests WHERE name = 'Achat nominal CB'), 1,   0,  30, 10),
((SELECT id FROM tests WHERE name = 'Achat nominal CB'), 2,  30,  60, 25),
((SELECT id FROM tests WHERE name = 'Achat nominal CB'), 3,  60,  90, 50),
((SELECT id FROM tests WHERE name = 'Achat nominal CB'), 4,  90, 120, 10);

GRANT ALL PRIVILEGES ON ALL TABLES    IN SCHEMA public TO scenario_user;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO scenario_user;

