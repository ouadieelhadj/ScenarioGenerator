-- Ports SWAM : networks devient la source de verite (branche au runtime cote SWAM)
BEGIN;
UPDATE networks SET
    issuer_host        = 'localhost',
    issuer_iso_port    = 8510,
    issuer_rest_port   = 8511,
    acquirer_host      = 'localhost',
    acquirer_rest_port = 8094
WHERE code = 'SWAM';
COMMIT;
