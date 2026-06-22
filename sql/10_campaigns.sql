-- ═══════════════════════════════════════════════════════════
-- Génération de scénarios monétiques — campagnes + transactions
-- Module sg-generator-orchestrator (user: scenario_user)
-- Campaign = modèle réutilisable (params). GeneratedTransaction = données générées.
-- ═══════════════════════════════════════════════════════════
\connect scenariogenerator

-- ----- Table des campagnes -----
CREATE TABLE IF NOT EXISTS campaigns (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(100) NOT NULL,
    description     VARCHAR(255),
    network         VARCHAR(20)  NOT NULL DEFAULT 'MASTERCARD',  -- MASTERCARD/VISA/CB
    channel         VARCHAR(20)  NOT NULL DEFAULT 'POS',         -- POS/ECOMMERCE/ATM
    country         VARCHAR(3)   NOT NULL DEFAULT 'FR',          -- FR/BE/ES/DE...
    currency        VARCHAR(3)   NOT NULL DEFAULT 'EUR',         -- EUR/USD/GBP
    amount_min      BIGINT       NOT NULL DEFAULT 1000,          -- centimes
    amount_max      BIGINT       NOT NULL DEFAULT 50000,         -- centimes
    mcc             VARCHAR(4)   DEFAULT '5999',
    tx_count        INTEGER      NOT NULL DEFAULT 100,
    tx_type         VARCHAR(30)  NOT NULL DEFAULT 'purchase',    -- type unique pour Phase 1 (mix plus tard)
    status          VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',       -- DRAFT/GENERATED
    created_at      TIMESTAMP    DEFAULT NOW()
);
ALTER TABLE campaigns OWNER TO scenario_user;

-- ----- Table des transactions générées -----
CREATE TABLE IF NOT EXISTS generated_transactions (
    id                      BIGSERIAL PRIMARY KEY,
    campaign_id             BIGINT NOT NULL REFERENCES campaigns(id) ON DELETE CASCADE,
    tx_type                 VARCHAR(30),                  -- purchase/refund...
    de2_pan                 VARCHAR(19),
    de3_processing_code     VARCHAR(6),
    de4_amount              BIGINT,                       -- centimes
    de7_transmission_dt     VARCHAR(10),                  -- MMDDhhmmss
    de11_stan               VARCHAR(6),
    de12_local_time         VARCHAR(6),                   -- HHmmss
    de13_local_date         VARCHAR(4),                   -- MMDD
    de14_expiry             VARCHAR(4),                   -- YYMM
    de18_mcc                VARCHAR(4),
    de22_pos_entry_mode     VARCHAR(3),
    de25_pos_condition      VARCHAR(2),
    de32_acquirer_id        VARCHAR(11),
    de37_rrn                VARCHAR(12),
    de41_terminal_id        VARCHAR(8),
    de42_merchant_id        VARCHAR(15),
    de43_merchant_name_loc  VARCHAR(40),
    de49_currency           VARCHAR(3),
    created_at              TIMESTAMP DEFAULT NOW()
);
ALTER TABLE generated_transactions OWNER TO scenario_user;

CREATE INDEX IF NOT EXISTS idx_gentx_campaign ON generated_transactions(campaign_id);
