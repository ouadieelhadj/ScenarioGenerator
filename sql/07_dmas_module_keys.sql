-- ═══════════════════════════════════════════════════════════
-- DMAS — tables de clés de travail par module (PEK/MAK sous LMK local)
-- Chaque module a SA table, possédée par SON user.
--   dmas_acq_keys : clé sous LMK ACQUÉREUR
--   dmas_iss_keys : clé sous LMK ÉMETTEUR
-- key_store reste réservé à l'ASCII existant.
-- ═══════════════════════════════════════════════════════════
\connect scenariogenerator

-- ── Table acquéreur ──
CREATE TABLE IF NOT EXISTS dmas_acq_keys (
    id               BIGSERIAL PRIMARY KEY,
    member_group_id  VARCHAR(20)  NOT NULL,
    key_type         VARCHAR(3)   NOT NULL,        -- PEK / MAK
    key_length       INTEGER      NOT NULL DEFAULT 24,
    key_under_lmk    VARCHAR(64),                  -- clé sous LMK acquéreur
    key_under_kek    VARCHAR(64),                  -- clé sous KEK (transport)
    kcv              VARCHAR(6),
    status           VARCHAR(10)  NOT NULL DEFAULT 'ACTIVE',
    created_at       TIMESTAMP    DEFAULT NOW(),
    CONSTRAINT uq_dmas_acq_keys UNIQUE (member_group_id, key_type, status)
);

-- ── Table émetteur ──
CREATE TABLE IF NOT EXISTS dmas_iss_keys (
    id               BIGSERIAL PRIMARY KEY,
    member_group_id  VARCHAR(20)  NOT NULL,
    key_type         VARCHAR(3)   NOT NULL,
    key_length       INTEGER      NOT NULL DEFAULT 24,
    key_under_lmk    VARCHAR(64),                  -- clé sous LMK émetteur
    key_under_kek    VARCHAR(64),
    kcv              VARCHAR(6),
    status           VARCHAR(10)  NOT NULL DEFAULT 'ACTIVE',
    created_at       TIMESTAMP    DEFAULT NOW(),
    CONSTRAINT uq_dmas_iss_keys UNIQUE (member_group_id, key_type, status)
);

-- ── Ownership / droits : chaque user possède sa table ──
ALTER TABLE dmas_acq_keys OWNER TO dmas_acquirer_user;
ALTER TABLE dmas_iss_keys OWNER TO dmas_issuer_user;

-- ddl-auto=validate : les entités sg-common voient les 2 tables -> SELECT croisé
GRANT SELECT ON dmas_acq_keys TO dmas_issuer_user;
GRANT SELECT ON dmas_iss_keys TO dmas_acquirer_user;
