-- ═══════════════════════════════════════════════════════════
-- ScenarioGenerator — Oracle Script
-- Schema : SCENARIO_USER
-- Pass   : postgres123
-- ═══════════════════════════════════════════════════════════

-- Création utilisateur
CREATE USER scenario_user IDENTIFIED BY postgres123;
GRANT CONNECT, RESOURCE, DBA TO scenario_user;
ALTER USER scenario_user QUOTA UNLIMITED ON USERS;

-- Se connecter en tant que scenario_user avant de continuer

-- Users
CREATE TABLE users (
    id          NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    login       VARCHAR2(50)  UNIQUE NOT NULL,
    password    VARCHAR2(255) NOT NULL,
    email       VARCHAR2(100),
    role        VARCHAR2(20)  NOT NULL,
    active      NUMBER(1)     DEFAULT 1,
    created_at  TIMESTAMP     DEFAULT SYSDATE,
    created_by  VARCHAR2(50),
    last_login  TIMESTAMP
);

-- Message Types
CREATE TABLE message_types (
    id               NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code             VARCHAR2(4)   NOT NULL,
    name             VARCHAR2(100) NOT NULL,
    category         VARCHAR2(50)  NOT NULL,
    description      VARCHAR2(255),
    processing_codes CLOB,
    active           NUMBER(1)  DEFAULT 1,
    created_at       TIMESTAMP  DEFAULT SYSDATE
);

-- Tests
CREATE TABLE tests (
    id               NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name             VARCHAR2(100) NOT NULL,
    description      VARCHAR2(255),
    category         VARCHAR2(50),
    message_type_id  NUMBER REFERENCES message_types(id),
    config           CLOB,
    expected_de039   VARCHAR2(2),
    active           NUMBER(1)  DEFAULT 1,
    created_at       TIMESTAMP  DEFAULT SYSDATE,
    created_by       NUMBER REFERENCES users(id)
);

-- TPS Steps
CREATE TABLE tps_steps (
    id            NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    test_id       NUMBER   NOT NULL REFERENCES tests(id) ON DELETE CASCADE,
    step_order    NUMBER   NOT NULL,
    start_seconds NUMBER   NOT NULL,
    end_seconds   NUMBER   NOT NULL,
    tps_value     NUMBER   NOT NULL
);

-- User Tests
CREATE TABLE user_tests (
    user_id     NUMBER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    test_id     NUMBER NOT NULL REFERENCES tests(id) ON DELETE CASCADE,
    assigned_at TIMESTAMP DEFAULT SYSDATE,
    assigned_by NUMBER REFERENCES users(id),
    PRIMARY KEY (user_id, test_id)
);

-- Executions
CREATE TABLE executions (
    id                  NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id             NUMBER        NOT NULL REFERENCES users(id),
    test_id             NUMBER        NOT NULL REFERENCES tests(id),
    mode                VARCHAR2(20)  NOT NULL,
    status              VARCHAR2(20)  NOT NULL,
    tps_target          NUMBER,
    duration_seconds    NUMBER,
    tx_total            NUMBER        DEFAULT 0,
    tx_sent             NUMBER        DEFAULT 0,
    tx_approved         NUMBER        DEFAULT 0,
    tx_declined         NUMBER        DEFAULT 0,
    tps_actual_avg      NUMBER(10,2),
    response_time_avg   NUMBER(10,2),
    response_time_min   NUMBER(10,2),
    response_time_max   NUMBER(10,2),
    response_time_p95   NUMBER(10,2),
    response_time_p99   NUMBER(10,2),
    started_at          TIMESTAMP     DEFAULT SYSDATE,
    ended_at            TIMESTAMP,
    report_dir          VARCHAR2(255),
    report_pdf          VARCHAR2(255),
    report_excel        VARCHAR2(255)
);

-- Results
CREATE TABLE results (
    id              NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    execution_id    NUMBER        NOT NULL REFERENCES executions(id) ON DELETE CASCADE,
    pan_masked      VARCHAR2(20),
    de039           VARCHAR2(2),
    de038_auth_code VARCHAR2(6),
    approved        NUMBER(1),
    duration_ms     NUMBER,
    request_hex     CLOB,
    response_hex    CLOB,
    executed_at     TIMESTAMP DEFAULT SYSDATE
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
        'admin@staging.com', 'ADMIN', 1, 'system');

-- Types de messages
INSERT INTO message_types (code, name, category, description, processing_codes) VALUES
('0100', 'Authorization Request', 'AUTHORIZATION', 'Mastercard authorization request',
'[{"code":"000000","label":"Purchase"},{"code":"010000","label":"Cash Advance"},{"code":"200000","label":"Refund"},{"code":"310000","label":"Balance Inquiry"},{"code":"340000","label":"Mini Statement"}]');

INSERT INTO message_types (code, name, category, description, processing_codes) VALUES
('0200', 'Financial Transaction Request', 'FINANCIAL', 'Financial transaction request',
'[{"code":"010000","label":"Cash Withdrawal"},{"code":"000000","label":"Purchase"}]');

INSERT INTO message_types (code, name, category, description, processing_codes) VALUES
('0400', 'Reversal Request', 'REVERSAL', 'Transaction reversal request',
'[{"code":"000000","label":"Purchase Reversal"},{"code":"010000","label":"Cash Reversal"}]');

INSERT INTO message_types (code, name, category, description, processing_codes) VALUES
('0800', 'Network Management Request', 'NETWORK', 'Network management message',
'[{"code":"301","label":"Sign-on"},{"code":"302","label":"Echo Test"}]');

INSERT INTO message_types (code, name, category, description, processing_codes) VALUES
('0820', 'Key Exchange Request', 'NETWORK', 'Key exchange message',
'[{"code":"101","label":"ZMK Exchange"},{"code":"102","label":"ZPK Exchange"},{"code":"103","label":"ZAK Exchange"}]');

COMMIT;
