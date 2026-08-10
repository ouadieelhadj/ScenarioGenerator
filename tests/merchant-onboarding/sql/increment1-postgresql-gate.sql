-- Read-only PostgreSQL gate for Merchant Portal increment 1.
-- Contains no credentials and returns only counts, identifiers and hashes.

SELECT 'merchant_onboarding_case' AS object, count(*) AS row_count,
       md5(COALESCE(string_agg(concat_ws('|', id::text,
           COALESCE(outlet_code, ''), COALESCE(outlet_name, ''),
           COALESCE(outlet_address, ''), COALESCE(country, '')),
           E'\n' ORDER BY id::text), '')) AS fingerprint
FROM merchant_onboarding_case;

SELECT 'merchant' AS object, count(*) AS row_count,
       md5(COALESCE(string_agg(id::text, E'\n' ORDER BY id::text), '')) AS fingerprint
FROM merchant;

SELECT 'merchant_outlet' AS object, count(*) AS row_count,
       md5(COALESCE(string_agg(concat_ws('|', id::text, merchant_id::text,
           outlet_code, address_line, country, active::text),
           E'\n' ORDER BY id::text), '')) AS fingerprint
FROM merchant_outlet;

SELECT 'onboarding_outlet' AS object, count(*) AS rows,
       count(DISTINCT id) AS distinct_ids,
       count(DISTINCT case_id) AS distinct_cases
FROM onboarding_outlet;

SELECT 'legacy_mapping' AS object, count(*) AS rows,
       count(DISTINCT case_id) AS distinct_cases,
       count(DISTINCT outlet_id) AS distinct_outlets
FROM legacy_outlet_migration;

SELECT 'migration_run' AS object, started_at, source_count, created_count,
       ignored_count, error_count, status
FROM migration_run
WHERE migration_code = 'MIG-001-LEGACY-OUTLET'
ORDER BY started_at;

SELECT 'onboarding_copy_mismatches' AS control, count(*) AS anomalies
FROM merchant_onboarding_case c
JOIN legacy_outlet_migration lm ON lm.case_id = c.id
JOIN onboarding_outlet o ON o.id = lm.outlet_id
WHERE o.outlet_code IS DISTINCT FROM c.outlet_code
   OR o.name IS DISTINCT FROM c.outlet_name
   OR o.address_line1 IS DISTINCT FROM c.outlet_address
   OR o.country IS DISTINCT FROM c.country;

SELECT 'onboarding_duplicate_case_code' AS control, count(*) AS anomalies
FROM (
    SELECT case_id, outlet_code
    FROM onboarding_outlet
    GROUP BY case_id, outlet_code
    HAVING count(*) > 1
) duplicates;

SELECT 'onboarding_principal_anomalies' AS control, count(*) AS anomalies
FROM (
    SELECT c.id
    FROM merchant_onboarding_case c
    LEFT JOIN onboarding_outlet o ON o.case_id = c.id AND o.active
    WHERE c.outlet_code IS NOT NULL
    GROUP BY c.id
    HAVING count(*) FILTER (WHERE o.principal) <> 1
) invalid_cases;

SELECT 'acquiring_active_merchant_principal_anomalies' AS control,
       count(*) AS anomalies
FROM (
    SELECT m.id
    FROM merchant m
    LEFT JOIN merchant_outlet o ON o.merchant_id = m.id AND o.active
    WHERE m.status = 'ACTIVE'
    GROUP BY m.id
    HAVING count(*) FILTER (WHERE o.principal) <> 1
) invalid_merchants;

SELECT 'acquiring_active_merchant_without_outlet' AS anomaly,
       m.id AS merchant_id, m.status,
       count(DISTINCT pc.id) AS contracts,
       count(DISTINCT es.id) AS ecommerce_stores
FROM merchant m
LEFT JOIN merchant_outlet o ON o.merchant_id = m.id AND o.active
LEFT JOIN payment_contract pc ON pc.customer_id = m.id::text
LEFT JOIN ecommerce_store es ON es.merchant_id = m.id
WHERE m.status = 'ACTIVE'
GROUP BY m.id, m.status
HAVING count(DISTINCT o.id) = 0;
