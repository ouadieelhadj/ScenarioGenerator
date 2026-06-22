-- ═══════════════════════════════════════════════════════════
-- Catalogue des BIN / plages de BIN (produits Mastercard, extensible)
-- Chaque entrée = un BIN précis (513330) OU une plage (51-55).
-- La campagne référence un bin_range (référence souple, sans FK stricte).
-- ═══════════════════════════════════════════════════════════
\connect scenariogenerator

CREATE TABLE IF NOT EXISTS bin_range (
    id            BIGSERIAL PRIMARY KEY,
    code          VARCHAR(20)  NOT NULL,          -- "513330" ou "51-55"
    product_name  VARCHAR(60)  NOT NULL,
    network       VARCHAR(20)  NOT NULL DEFAULT 'MASTERCARD',
    pan_length    INTEGER      NOT NULL DEFAULT 16,
    is_range      BOOLEAN      NOT NULL DEFAULT FALSE,
    enabled       BOOLEAN      NOT NULL DEFAULT TRUE
);
ALTER TABLE bin_range OWNER TO scenario_user;

-- Seed : quelques produits Mastercard (BIN précis + plages)
INSERT INTO bin_range (code, product_name, network, pan_length, is_range) VALUES
  ('51-55',   'Mastercard (plage classique 51-55)', 'MASTERCARD', 16, TRUE),
  ('2221-2720','Mastercard (nouvelle plage 2-series)','MASTERCARD',16, TRUE),
  ('513330',  'Mastercard Standard (BIN test)',     'MASTERCARD', 16, FALSE),
  ('541333',  'Mastercard Gold (BIN test)',         'MASTERCARD', 16, FALSE),
  ('555555',  'Mastercard World (BIN test)',        'MASTERCARD', 16, FALSE)
ON CONFLICT DO NOTHING;

-- Référence souple dans la campagne (pas de FK stricte)
ALTER TABLE campaigns ADD COLUMN IF NOT EXISTS bin_range_id BIGINT;
