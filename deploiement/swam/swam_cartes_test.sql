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

-- Reinitialisation volontaire des cartes reservees a l'E2E. Elle garantit
-- qu'une nouvelle campagne ne depend pas des soldes/statuts d'une campagne
-- precedente. Ces PAN sont exclusivement des valeurs sandbox.
UPDATE issuer_swam_cards
   SET pin='1234', balance=100000, currency='504', expiry='2812', status='ACTIVE'
 WHERE pan='5321962145453348';
UPDATE issuer_swam_cards
   SET pin='1234', balance=500, currency='504', expiry='2812', status='ACTIVE'
 WHERE pan='5321000000000011';
UPDATE issuer_swam_cards
   SET pin='1234', balance=100000, currency='504', expiry='2812', status='BLOCKED'
 WHERE pan='5321000000000029';

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

UPDATE acquirer_swam_cards
   SET pin='1234', balance=100000, currency='504', expiry='2812', status='ACTIVE'
 WHERE pan='5321962145453355';
UPDATE acquirer_swam_cards
   SET pin='1234', balance=500, currency='504', expiry='2812', status='ACTIVE'
 WHERE pan='5321962145453363';
UPDATE acquirer_swam_cards
   SET pin='1234', balance=100000, currency='504', expiry='2812', status='BLOCKED'
 WHERE pan='5321962145453371';
