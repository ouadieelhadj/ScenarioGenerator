-- ═══════════════════════════════════════════════════════════
-- DMAS — Table dmas_kek (KEK sous 2 LMK distincts)
-- Le KEK n'existe en clair nulle part de façon persistante en prod ;
-- ici kek_clear est conservé pour debug/simulation.
-- kek_under_acq_lmk / kek_under_iss_lmk = même KEK chiffré sous
-- le LMK de chaque module (formé via formKEYfromClearComponents).
-- ═══════════════════════════════════════════════════════════

\connect scenariogenerator

CREATE TABLE IF NOT EXISTS dmas_kek (
    id                 BIGSERIAL PRIMARY KEY,
    member_group_id    VARCHAR(20)  NOT NULL,
    key_length         INTEGER      NOT NULL DEFAULT 24,
    kek_clear          VARCHAR(48),                 -- debug/simulation uniquement
    kek_under_acq_lmk  VARCHAR(128),                -- SecureDESKey hex sous LMK acquéreur
    kek_under_iss_lmk  VARCHAR(128),                -- SecureDESKey hex sous LMK émetteur
    kcv                VARCHAR(6),
    status             VARCHAR(10)  NOT NULL DEFAULT 'ACTIVE',
    description        VARCHAR(255),
    created_at         TIMESTAMP    DEFAULT NOW(),
    CONSTRAINT chk_dmas_kek_status CHECK (status IN ('ACTIVE','INACTIVE','PENDING')),
    CONSTRAINT uq_dmas_kek_group UNIQUE (member_group_id)
);
CREATE INDEX IF NOT EXISTS idx_dmas_kek_group ON dmas_kek(member_group_id);

-- Les deux modules lisent/écrivent dmas_kek (bootstrap + lecture au runtime)
GRANT ALL PRIVILEGES ON dmas_kek TO dmas_acquirer_user, dmas_issuer_user;
GRANT USAGE, SELECT ON dmas_kek_id_seq TO dmas_acquirer_user, dmas_issuer_user;
