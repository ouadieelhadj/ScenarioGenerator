-- Decision projet : PAN complet conserve dans Issuing avec token opaque bijectif.
-- Le PAN ne doit jamais etre copie dans les logs, outbox ou reponses API.

ALTER TABLE issuing_payment_identifier
    ADD COLUMN IF NOT EXISTS pan_clear VARCHAR(19);

ALTER TABLE issuing_payment_identifier
    ADD CONSTRAINT ck_issuing_pan_clear_format
    CHECK (pan_clear IS NULL OR pan_clear ~ '^[0-9]{12,19}$');

CREATE UNIQUE INDEX IF NOT EXISTS uk_issuing_identifier_pan
    ON issuing_payment_identifier (issuer_id, pan_clear)
    WHERE pan_clear IS NOT NULL;

-- vault_reference devient le token opaque local. Le nom physique historique
-- est conserve pour rendre la migration append-only et compatible V2.
CREATE UNIQUE INDEX IF NOT EXISTS uk_issuing_identifier_token
    ON issuing_payment_identifier (issuer_id, vault_reference);
