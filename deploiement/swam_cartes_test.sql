-- Cartes de test SWAM (idempotent)
INSERT INTO swam_cards (pan, pin, balance, currency, expiry, status)
SELECT '5321962145453348', '1234', 100000, '504', '2812', 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM swam_cards WHERE pan='5321962145453348');

INSERT INTO swam_cards (pan, pin, balance, currency, expiry, status)
SELECT '5321000000000011', '1234', 500, '504', '2812', 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM swam_cards WHERE pan='5321000000000011');

INSERT INTO swam_cards (pan, pin, balance, currency, expiry, status)
SELECT '5321000000000029', '1234', 100000, '504', '2812', 'BLOCKED'
WHERE NOT EXISTS (SELECT 1 FROM swam_cards WHERE pan='5321000000000029');
