-- ═══════════════════════════════════════════════════════════
-- Persistance des exécutions (rejeux) de campagne — Phase 2b
-- Entité dédiée, découplée du workflow Test/Execution existant.
-- Persistance APRÈS la fin du rejeu (pas pendant) pour ne pas perturber le débit.
-- ═══════════════════════════════════════════════════════════
\connect scenariogenerator

-- ----- L'exécution d'une campagne (un rejeu) -----
CREATE TABLE IF NOT EXISTS campaign_executions (
    id                 BIGSERIAL PRIMARY KEY,
    campaign_id        BIGINT NOT NULL,
    status             VARCHAR(20) NOT NULL DEFAULT 'RUNNING',  -- RUNNING/COMPLETED/FAILED
    tx_total           INTEGER DEFAULT 0,
    tx_approved        INTEGER DEFAULT 0,
    tx_declined        INTEGER DEFAULT 0,
    tx_errors          INTEGER DEFAULT 0,
    response_time_avg  NUMERIC(10,2),
    response_time_min  NUMERIC(10,2),
    response_time_max  NUMERIC(10,2),
    response_time_p95  NUMERIC(10,2),
    response_time_p99  NUMERIC(10,2),
    started_at         TIMESTAMP DEFAULT NOW(),
    ended_at           TIMESTAMP
);
ALTER TABLE campaign_executions OWNER TO scenario_user;
CREATE INDEX IF NOT EXISTS idx_campexec_campaign ON campaign_executions(campaign_id);

-- ----- Tableau de bord : répartition par code réponse DE39 -----
CREATE TABLE IF NOT EXISTS campaign_execution_de39_stats (
    id            BIGSERIAL PRIMARY KEY,
    execution_id  BIGINT NOT NULL REFERENCES campaign_executions(id) ON DELETE CASCADE,
    de39          VARCHAR(3) NOT NULL,
    count         INTEGER NOT NULL DEFAULT 0
);
ALTER TABLE campaign_execution_de39_stats OWNER TO scenario_user;
CREATE INDEX IF NOT EXISTS idx_de39stats_exec ON campaign_execution_de39_stats(execution_id);

-- ----- Détail transaction par transaction (OPTIONNEL selon flag YAML) -----
CREATE TABLE IF NOT EXISTS campaign_execution_results (
    id            BIGSERIAL PRIMARY KEY,
    execution_id  BIGINT NOT NULL REFERENCES campaign_executions(id) ON DELETE CASCADE,
    pan_masked    VARCHAR(20),
    de39          VARCHAR(3),
    approved      BOOLEAN,
    duration_ms   INTEGER,
    executed_at   TIMESTAMP DEFAULT NOW()
);
ALTER TABLE campaign_execution_results OWNER TO scenario_user;
CREATE INDEX IF NOT EXISTS idx_campresult_exec ON campaign_execution_results(execution_id);
