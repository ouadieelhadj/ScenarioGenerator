-- Aligne toutes les sequences SERIAL/BIGSERIAL sur les donnees chargees.
DO $$
DECLARE
    item RECORD;
    maximum BIGINT;
BEGIN
    FOR item IN
        SELECT
            sequence_ns.nspname AS sequence_schema,
            sequence_class.relname AS sequence_name,
            table_ns.nspname AS table_schema,
            table_class.relname AS table_name,
            attribute.attname AS column_name
        FROM pg_class sequence_class
        JOIN pg_namespace sequence_ns
          ON sequence_ns.oid = sequence_class.relnamespace
        JOIN pg_depend dependency
          ON dependency.objid = sequence_class.oid
         AND dependency.deptype IN ('a', 'i')
        JOIN pg_class table_class
          ON table_class.oid = dependency.refobjid
        JOIN pg_namespace table_ns
          ON table_ns.oid = table_class.relnamespace
        JOIN pg_attribute attribute
          ON attribute.attrelid = table_class.oid
         AND attribute.attnum = dependency.refobjsubid
        WHERE sequence_class.relkind = 'S'
          AND sequence_ns.nspname = 'public'
    LOOP
        EXECUTE format(
            'SELECT max(%I) FROM %I.%I',
            item.column_name, item.table_schema, item.table_name
        ) INTO maximum;

        IF maximum IS NULL THEN
            EXECUTE format(
                'SELECT setval(%L, 1, false)',
                item.sequence_schema || '.' || item.sequence_name
            );
        ELSE
            EXECUTE format(
                'SELECT setval(%L, %s, true)',
                item.sequence_schema || '.' || item.sequence_name,
                maximum
            );
        END IF;
    END LOOP;
END $$;
