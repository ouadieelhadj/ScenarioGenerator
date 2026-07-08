-- =====================================================================
-- Migration v1.2.0 : referentiel des reseaux (networks)
-- Famille A = protocole (verifie dans le code pour DMAS).
-- Famille B = infra (ports/hosts). DMAS peuples, SWAM NULL.
-- =====================================================================
BEGIN;

CREATE TABLE IF NOT EXISTS networks (
    id                      BIGSERIAL PRIMARY KEY,
    code                    VARCHAR(20)  NOT NULL UNIQUE,
    name                    VARCHAR(100) NOT NULL,
    description             VARCHAR(255),
    -- Famille A : protocole
    iso_version             VARCHAR(20),
    length_prefix_size      INT,
    length_prefix_encoding  VARCHAR(10),      -- ASCII | BINARY
    header_type             VARCHAR(20),      -- NONE | POWERCARD | TPDU
    default_field_encoding  VARCHAR(10),      -- ASCII | EBCDIC | BCD
    mac_present             BOOLEAN DEFAULT true,
    pin_block_format        VARCHAR(20),
    packager_class          VARCHAR(255),     -- pointeur vers la classe Java
    -- Famille B : infra
    acquirer_host           VARCHAR(100),
    acquirer_rest_port      INT,
    acquirer_jpos_port      INT,
    issuer_host             VARCHAR(100),
    issuer_rest_port        INT,
    issuer_iso_port         INT,
    orchestrator_port       INT,
    active                  BOOLEAN NOT NULL DEFAULT true,
    created_at              TIMESTAMP DEFAULT now()
);

-- DMAS : valeurs VERIFIEES dans le code
--   DmasLengthChannel = 2 octets big-endian (BINARY), pas de header
--   McPackagerEbcdic = champs EBCDIC
--   ports : acq REST 8084 / jPOS 8600 ; iss REST 8501 / ISO 8500 ; orch 8080
INSERT INTO networks (code, name, description, iso_version,
    length_prefix_size, length_prefix_encoding, header_type,
    default_field_encoding, mac_present, pin_block_format, packager_class,
    acquirer_host, acquirer_rest_port, acquirer_jpos_port,
    issuer_host, issuer_rest_port, issuer_iso_port, orchestrator_port, active)
SELECT 'DMAS', 'Mastercard DMAS', 'Reseau Mastercard DMAS (existant)',
    'ISO8583:1987', 2, 'BINARY', 'NONE', 'EBCDIC', true, 'ANSI_0',
    'com.staging.sg.common.iso.McPackagerEbcdic',
    'localhost', 8084, 8600, 'localhost', 8501, 8500, 8080, true
WHERE NOT EXISTS (SELECT 1 FROM networks WHERE code='DMAS');

-- SWAM : depuis la spec HPS SID v3.20 (A VALIDER a l'implementation du packager)
--   4 octets ASCII + header PowerCARD, champs ASCII.
--   Ports acquereur/issuer NULL : modules pas encore crees.
INSERT INTO networks (code, name, description, iso_version,
    length_prefix_size, length_prefix_encoding, header_type,
    default_field_encoding, mac_present, pin_block_format, packager_class,
    acquirer_host, acquirer_rest_port, acquirer_jpos_port,
    issuer_host, issuer_rest_port, issuer_iso_port, orchestrator_port, active)
SELECT 'SWAM', 'Switch Al Maghrib', 'Switch national marocain (HPS HSID/PowerCARD)',
    'ISO8583:1993', 4, 'ASCII', 'POWERCARD', 'ASCII', true, 'ANSI_0',
    'com.staging.sg.common.iso.SwamPackager',
    'localhost', NULL, NULL, 'localhost', NULL, NULL, 8080, true
WHERE NOT EXISTS (SELECT 1 FROM networks WHERE code='SWAM');

-- FK : garantit qu'aucune campagne / type ne reference un reseau inexistant.
-- (les valeurs actuelles DMAS/SWAM existent deja dans networks -> pas de rejet)
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname='fk_message_types_network') THEN
        ALTER TABLE message_types
          ADD CONSTRAINT fk_message_types_network
          FOREIGN KEY (network) REFERENCES networks(code);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname='fk_campaigns_network') THEN
        ALTER TABLE campaigns
          ADD CONSTRAINT fk_campaigns_network
          FOREIGN KEY (network) REFERENCES networks(code);
    END IF;
END $$;

COMMIT;
