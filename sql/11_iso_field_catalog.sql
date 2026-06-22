-- ═══════════════════════════════════════════════════════════
-- Catalogue global des champs ISO (référence système, enrichissable)
-- + sélection des champs par campagne (colonne selected_fields)
-- ═══════════════════════════════════════════════════════════
\connect scenariogenerator

-- ----- Catalogue des champs ISO -----
CREATE TABLE IF NOT EXISTS iso_field_catalog (
    id            BIGSERIAL PRIMARY KEY,
    field_code    VARCHAR(10)  NOT NULL UNIQUE,   -- DE2, DE3...
    name          VARCHAR(60)  NOT NULL,
    description   VARCHAR(255),
    gen_strategy  VARCHAR(40)  NOT NULL,          -- PAN_LUHN, AMOUNT_RANGE, STAN, DATE_NOW...
    enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    display_order INTEGER      NOT NULL DEFAULT 0
);
ALTER TABLE iso_field_catalog OWNER TO scenario_user;

-- ----- Seed du catalogue (champs ISO connus) -----
INSERT INTO iso_field_catalog (field_code, name, description, gen_strategy, display_order) VALUES
  ('DE2',  'PAN',                   'Primary Account Number',        'PAN_LUHN',      10),
  ('DE3',  'Processing Code',       'Type de transaction',           'PROCESSING_CODE',20),
  ('DE4',  'Transaction Amount',    'Montant en centimes',           'AMOUNT_RANGE',  30),
  ('DE7',  'Transmission DateTime', 'MMDDhhmmss UTC',                'DATE_NOW',      40),
  ('DE11', 'STAN',                  'System Trace Audit Number',     'STAN',          50),
  ('DE12', 'Local Transaction Time','HHmmss',                        'LOCAL_TIME',    60),
  ('DE13', 'Local Transaction Date','MMDD',                          'LOCAL_DATE',    70),
  ('DE14', 'Expiration Date',       'YYMM (futur)',                  'EXPIRY',        80),
  ('DE18', 'MCC',                   'Merchant Category Code',        'MCC',           90),
  ('DE22', 'POS Entry Mode',        'Mode de saisie',                'POS_ENTRY',    100),
  ('DE25', 'POS Condition Code',    'Condition POS',                 'POS_CONDITION',110),
  ('DE32', 'Acquirer ID',           'Acquiring Institution ID',      'ACQUIRER_ID',  120),
  ('DE37', 'RRN',                   'Retrieval Reference Number',    'RRN',          130),
  ('DE41', 'Terminal ID',           'Acceptor Terminal ID',          'TERMINAL_ID',  140),
  ('DE42', 'Merchant ID',           'Acceptor ID Code',              'MERCHANT_ID',  150),
  ('DE43', 'Merchant Name/Location','Nom + ville + pays (ans-40)',   'MERCHANT_NAME',160),
  ('DE49', 'Currency Code',         'Devise (n-3)',                  'CURRENCY',     170)
ON CONFLICT (field_code) DO NOTHING;

-- ----- Sélection des champs par campagne -----
ALTER TABLE campaigns ADD COLUMN IF NOT EXISTS selected_fields TEXT;
