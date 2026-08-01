-- Le contrat carte Issuing devient le contrat de paiement partage avec
-- Acquisition. Les lignes existantes sont conservees et typees ISSUING_CARD.

ALTER TABLE issuing_card_contract RENAME TO payment_contract;
ALTER TABLE payment_contract RENAME COLUMN issuer_id TO institution_id;
ALTER TABLE payment_contract RENAME COLUMN cardholder_id TO beneficiary_id;

ALTER TABLE payment_contract
    ADD COLUMN contract_type VARCHAR(32) NOT NULL DEFAULT 'ISSUING_CARD',
    ADD COLUMN parent_contract_id UUID;

ALTER TABLE payment_contract
    ALTER COLUMN contract_type DROP DEFAULT,
    ALTER COLUMN funding_contract_id DROP NOT NULL,
    DROP CONSTRAINT fk_issuing_contract_product,
    ADD CONSTRAINT ck_payment_contract_type CHECK (
        contract_type IN ('ISSUING_CARD', 'ACQUIRING_MERCHANT', 'ACQUIRING_DEVICE')
    ),
    ADD CONSTRAINT fk_payment_contract_parent
        FOREIGN KEY (parent_contract_id) REFERENCES payment_contract (id),
    ADD CONSTRAINT ck_payment_contract_parent CHECK (
        (contract_type = 'ACQUIRING_DEVICE' AND parent_contract_id IS NOT NULL)
        OR (contract_type <> 'ACQUIRING_DEVICE' AND parent_contract_id IS NULL)
    );

ALTER TABLE payment_contract
    RENAME CONSTRAINT uk_issuing_contract_external
    TO uk_payment_contract_external;
ALTER TABLE payment_contract
    RENAME CONSTRAINT uk_issuing_contract_idempotency
    TO uk_payment_contract_idempotency;
ALTER TABLE issuing_card_instrument
    RENAME CONSTRAINT fk_issuing_instrument_contract
    TO fk_issuing_instrument_payment_contract;

CREATE INDEX idx_payment_contract_parent
    ON payment_contract (institution_id, parent_contract_id);
CREATE INDEX idx_payment_contract_type_status
    ON payment_contract (institution_id, contract_type, status);
