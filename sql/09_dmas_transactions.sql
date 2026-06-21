-- DMAS — table des transactions (côté BANQUE émettrice / issuer)
-- Stocke chaque 0100 approuvé pour permettre le reversal (0400).
\connect scenariogenerator

CREATE TABLE IF NOT EXISTS dmas_transactions (
    id              BIGSERIAL PRIMARY KEY,
    pan             VARCHAR(19)  NOT NULL,
    stan            VARCHAR(6)   NOT NULL,
    transmission_dt VARCHAR(10)  NOT NULL,
    mti             VARCHAR(4)   NOT NULL,
    processing_code VARCHAR(6),
    amount          BIGINT       NOT NULL,
    currency        VARCHAR(3),
    response_code   VARCHAR(2),
    status          VARCHAR(10)  NOT NULL DEFAULT 'APPROVED',
    created_at      TIMESTAMP    DEFAULT NOW(),
    reversed_at     TIMESTAMP,
    CONSTRAINT uq_dmas_tx UNIQUE (stan, transmission_dt)
);

CREATE INDEX IF NOT EXISTS idx_dmas_tx_pan ON dmas_transactions(pan);

ALTER TABLE dmas_transactions OWNER TO dmas_issuer_user;

GRANT SELECT ON dmas_transactions TO dmas_acquirer_user;
GRANT USAGE, SELECT ON dmas_transactions_id_seq TO dmas_acquirer_user;
