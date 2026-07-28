-- Cartes du switch/emetteur SWAM (idempotent).
INSERT INTO issuer_swam_cards (pan, pin, balance, currency, expiry, status)
SELECT '5321962145453348', '1234', 100000, '504', '2812', 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM issuer_swam_cards WHERE pan='5321962145453348');

INSERT INTO issuer_swam_cards (pan, pin, balance, currency, expiry, status)
SELECT '5321000000000011', '1234', 500, '504', '2812', 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM issuer_swam_cards WHERE pan='5321000000000011');

INSERT INTO issuer_swam_cards (pan, pin, balance, currency, expiry, status)
SELECT '5321000000000029', '1234', 100000, '504', '2812', 'BLOCKED'
WHERE NOT EXISTS (SELECT 1 FROM issuer_swam_cards WHERE pan='5321000000000029');

-- Cartes du membre/acquereur SWAM : PAN distincts pour verifier l'isolation.
INSERT INTO acquirer_swam_cards (pan, pin, balance, currency, expiry, status)
SELECT '5321962145453355', '1234', 100000, '504', '2812', 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM acquirer_swam_cards WHERE pan='5321962145453355');

INSERT INTO acquirer_swam_cards (pan, pin, balance, currency, expiry, status)
SELECT '5321962145453363', '1234', 500, '504', '2812', 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM acquirer_swam_cards WHERE pan='5321962145453363');

INSERT INTO acquirer_swam_cards (pan, pin, balance, currency, expiry, status)
SELECT '5321962145453371', '1234', 100000, '504', '2812', 'BLOCKED'
WHERE NOT EXISTS (SELECT 1 FROM acquirer_swam_cards WHERE pan='5321962145453371');
