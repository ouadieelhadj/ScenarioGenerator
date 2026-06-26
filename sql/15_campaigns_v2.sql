-- ═══════════════════════════════════════════════════════════
-- Modele CAMPAGNE (nouveau) - iteration 1 : socle + multi-paliers + SLA/verdict
-- Remplace l'ancien systeme Replay (tables droppees ci-dessous).
-- Reutilise le moteur de charge existant (LoadTestService cote acquereur).
-- ═══════════════════════════════════════════════════════════

-- ----- DROP des anciennes tables (ancien systeme Replay, residus de test) -----
DROP TABLE IF EXISTS campaign_execution_results   CASCADE;
DROP TABLE IF EXISTS campaign_execution_de39_stats CASCADE;
DROP TABLE IF EXISTS campaign_executions          CASCADE;
DROP TABLE IF EXISTS generated_transactions       CASCADE;
DROP TABLE IF EXISTS campaigns                     CASCADE;

-- ----- Definition d'une campagne (reprend Test, enrichi SLA) -----
CREATE TABLE campaigns (
    id                  BIGSERIAL PRIMARY KEY,
    name                VARCHAR(100) NOT NULL,
    description         VARCHAR(255),
    category            VARCHAR(50),
    config              TEXT,
    expected_de039      VARCHAR(2),
    active              BOOLEAN   DEFAULT TRUE,
    created_at          TIMESTAMP DEFAULT NOW(),
    created_by          BIGINT REFERENCES users(id),
    sla_p95_max_ms      INTEGER,
    sla_error_rate_max  NUMERIC(5,2),
    sla_approval_min    NUMERIC(5,2),
    stop_on_error_rate  NUMERIC(5,2)
);
ALTER TABLE campaigns OWNER TO scenario_user;

-- ----- Paliers de charge (reprend tps_steps + concurrency optionnelle) -----
CREATE TABLE campaign_load_steps (
    id            BIGSERIAL PRIMARY KEY,
    campaign_id   BIGINT  NOT NULL REFERENCES campaigns(id) ON DELETE CASCADE,
    step_order    INTEGER NOT NULL,
    start_seconds INTEGER NOT NULL,
    end_seconds   INTEGER NOT NULL,
    tps_value     INTEGER NOT NULL,
    concurrency   INTEGER
);
ALTER TABLE campaign_load_steps OWNER TO scenario_user;
CREATE INDEX idx_camploadsteps_campaign ON campaign_load_steps(campaign_id);

-- ----- Execution d'une campagne (reprend Execution + verdict SLA) -----
CREATE TABLE campaign_executions (
    id                  BIGSERIAL PRIMARY KEY,
    campaign_id         BIGINT       NOT NULL REFERENCES campaigns(id),
    user_id             BIGINT       NOT NULL REFERENCES users(id),
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
    verdict             VARCHAR(10),
    verdict_detail      VARCHAR(255),
    started_at          TIMESTAMP    DEFAULT NOW(),
    ended_at            TIMESTAMP,
    report_dir          VARCHAR(255),
    report_pdf          VARCHAR(255),
    report_excel        VARCHAR(255)
);
ALTER TABLE campaign_executions OWNER TO scenario_user;
CREATE INDEX idx_campexec_campaign ON campaign_executions(campaign_id);
CREATE INDEX idx_campexec_user     ON campaign_executions(user_id);
CREATE INDEX idx_campexec_status   ON campaign_executions(status);

-- ----- Detail par transaction (reprend results + step_order) -----
CREATE TABLE campaign_execution_results (
    id              BIGSERIAL PRIMARY KEY,
    execution_id    BIGINT      NOT NULL REFERENCES campaign_executions(id) ON DELETE CASCADE,
    step_order      INTEGER,
    pan_masked      VARCHAR(20),
    de039           VARCHAR(2),
    de038_auth_code VARCHAR(6),
    approved        BOOLEAN,
    duration_ms     INTEGER,
    request_hex     TEXT,
    response_hex    TEXT,
    executed_at     TIMESTAMP DEFAULT NOW()
);
ALTER TABLE campaign_execution_results OWNER TO scenario_user;
CREATE INDEX idx_campexecres_exec  ON campaign_execution_results(execution_id);
CREATE INDEX idx_campexecres_de039 ON campaign_execution_results(de039);
