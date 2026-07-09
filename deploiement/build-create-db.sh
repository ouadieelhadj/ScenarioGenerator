#!/usr/bin/env bash
set +H
set -euo pipefail
# ================================================================
# build-create-db.sh
# Genere create-db.sql : script SQL 100% autonome qui recrée
# la base scenariogenerator (schema + donnees ref + FK + index).
# N'appelle AUCUN fichier externe ni pg_dump.
# Usage : bash deploiement/build-create-db.sh
# ================================================================
DEPLOY="$(cd "$(dirname "$0")" && pwd)"
OUT="$DEPLOY/create-db.sql"

# Auto-detection psql
PSQL=""
for try in \
  "/d/MoneyCore/PostgreSQL/18/bin/psql.exe" \
  "/f/MoneyCore/pgsql/bin/psql.exe" \
  "/d/MoneyCore/pgsql/bin/psql.exe" \
  "/f/MoneyCore/PostgreSQL/18/bin/psql.exe"; do
  [ -f "$try" ] && { PSQL="$try"; break; }
done
[ -z "$PSQL" ] && { echo "ERREUR : psql introuvable"; exit 1; }
echo "psql : $PSQL"
echo "output : $OUT"
export PGPASSWORD="postgres123"
export PSQL_PATH="$PSQL"
export OUT_PATH="$OUT"

python - << 'PYEOF'
import subprocess, os, sys, csv, io
from pathlib import Path

PSQL   = os.environ["PSQL_PATH"]
OUT    = os.environ["OUT_PATH"]
ENV    = {**os.environ, "PGPASSWORD": "postgres123"}
DB     = "scenariogenerator"
DBPASS = "postgres123"

def q(sql, db=DB):
    """Execute une requete psql, retourne liste de listes."""
    r = subprocess.run(
        [PSQL, "-h","localhost","-p","5432","-U","postgres",
         "-d", db, "-t", "-A", "-F", "\t", "-c", sql],
        capture_output=True, text=True, env=ENV)
    if r.returncode != 0:
        raise RuntimeError(f"psql: {r.stderr.strip()}")
    lines = [l for l in r.stdout.split("\n") if l.strip()]
    return [l.split("\t") for l in lines]

def qcsv(table):
    """COPY table TO STDOUT CSV -> (headers, rows)."""
    r = subprocess.run(
        [PSQL, "-h","localhost","-p","5432","-U","postgres","-d", DB,
         "-c", f"\\COPY public.{table} TO STDOUT WITH (FORMAT CSV, HEADER true, NULL '\\\\N')"],
        capture_output=True, text=True, env=ENV)
    if r.returncode != 0 or not r.stdout.strip():
        return [], []
    reader = list(csv.reader(io.StringIO(r.stdout)))
    return (reader[0], reader[1:]) if reader else ([], [])

def ql(v):
    """quote_literal : echappe une valeur pour SQL."""
    if v is None or v == "\\N":
        return "NULL"
    v = str(v).replace("'","''")
    return f"'{v}'"

# ---------------------------------------------------------------
lines = []
w = lines.append

w("-- ================================================================")
w("-- create-db.sql — Base scenariogenerator COMPLETE")
w("-- Genere par build-create-db.sh (aucune dependance externe).")
w("-- Usage : psql -U postgres -f create-db.sql")
w("-- ================================================================")
w("SET client_encoding = 'UTF8';")
w("SET standard_conforming_strings = on;")
w("")

# --- Users ---
w("-- 1. Users applicatifs")
w("DO $$")
w("BEGIN")
for role, pwd in [
    ("scenario_user",     DBPASS),
    ("dmas_acquirer_user",DBPASS),
    ("dmas_issuer_user",  DBPASS),
    ("swam_issuer_user",  DBPASS),
    ("swam_acquirer_user",DBPASS),
]:
    w(f"  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname='{role}')")
    w(f"    THEN CREATE ROLE {role} LOGIN PASSWORD '{pwd}'; END IF;")
w("END $$;")
w("")

# --- Base ---
w("-- 2. Base de donnees")
w("DROP DATABASE IF EXISTS scenariogenerator;")
w("CREATE DATABASE scenariogenerator OWNER scenario_user;")
w("\\connect scenariogenerator")
w("GRANT ALL ON SCHEMA public TO scenario_user, dmas_acquirer_user, dmas_issuer_user, swam_issuer_user, swam_acquirer_user;")
w("")

# --- Sequences ---
w("-- 3. Sequences")
seqs = q("""
SELECT s.sequence_name, s.start_value, s.minimum_value, s.maximum_value, s.increment
FROM information_schema.sequences s
WHERE s.sequence_schema = 'public'
ORDER BY s.sequence_name;
""")
for row in seqs:
    if len(row) >= 1 and row[0]:
        sname, start, minv, maxv, inc = row[0], row[1], row[2], row[3], row[4]
        w(f"CREATE SEQUENCE public.{sname}")
        w(f"    START WITH {start}")
        w(f"    INCREMENT BY {inc}")
        w(f"    MINVALUE {minv}")
        if maxv and int(maxv) < 9000000000000000000:
            w(f"    MAXVALUE {maxv}")
        else:
            w(f"    NO MAXVALUE")
        w(f"    CACHE 1;")
w("")

# --- Tables (sans FK) ---
w("-- 4. Tables (sans contraintes FK)")
tables = q("""
SELECT table_name FROM information_schema.tables
WHERE table_schema='public' AND table_type='BASE TABLE'
ORDER BY table_name;
""")
table_names = [r[0] for r in tables if r[0]]

for tbl in table_names:
    cols = q(f"""
SELECT column_name,
       CASE
         WHEN data_type='character varying' AND character_maximum_length IS NOT NULL
           THEN 'character varying('||character_maximum_length||')'
         WHEN data_type='character varying' THEN 'character varying'
         WHEN data_type='character' AND character_maximum_length IS NOT NULL
           THEN 'character('||character_maximum_length||')'
         WHEN data_type='numeric' AND numeric_precision IS NOT NULL
           THEN 'numeric('||numeric_precision||','||COALESCE(numeric_scale::text,'0')||')'
         ELSE data_type
       END,
       is_nullable,
       column_default
FROM information_schema.columns
WHERE table_schema='public' AND table_name='{tbl}'
ORDER BY ordinal_position;
""")
    if not cols or not cols[0][0]:
        continue

    pks = q(f"""
SELECT kcu.column_name
FROM information_schema.table_constraints tc
JOIN information_schema.key_column_usage kcu
  ON tc.constraint_name=kcu.constraint_name AND tc.table_schema=kcu.table_schema
WHERE tc.table_schema='public' AND tc.table_name='{tbl}'
  AND tc.constraint_type='PRIMARY KEY'
ORDER BY kcu.ordinal_position;
""")
    pk_cols = [r[0] for r in pks if r[0]]

    uqs = q(f"""
SELECT tc.constraint_name, kcu.column_name
FROM information_schema.table_constraints tc
JOIN information_schema.key_column_usage kcu
  ON tc.constraint_name=kcu.constraint_name AND tc.table_schema=kcu.table_schema
WHERE tc.table_schema='public' AND tc.table_name='{tbl}'
  AND tc.constraint_type='UNIQUE'
ORDER BY tc.constraint_name, kcu.ordinal_position;
""")
    uq_map = {}
    for row in uqs:
        if row[0]: uq_map.setdefault(row[0],[]).append(row[1])

    w(f"CREATE TABLE public.{tbl} (")
    col_defs = []
    for col in cols:
        cname, ctype, nullable, default = col[0], col[1], col[2], col[3] if len(col)>3 else None
        if not cname: continue
        d = f"    {cname} {ctype}"
        if default and default.strip():
            d += f" DEFAULT {default}"
        if nullable == "NO":
            d += " NOT NULL"
        col_defs.append(d)
    if pk_cols:
        col_defs.append(f"    CONSTRAINT {tbl}_pkey PRIMARY KEY ({', '.join(pk_cols)})")
    for cname, ucols in uq_map.items():
        col_defs.append(f"    CONSTRAINT {cname} UNIQUE ({', '.join(ucols)})")
    w(",\n".join(col_defs))
    w(");")
    # Sequence ownership
    for col in cols:
        cname, _, _, default = col[0], col[1], col[2], col[3] if len(col)>3 else ""
        if default and "nextval" in (default or ""):
            seq = default.split("'")[1].replace("public.","") if "'" in default else ""
            if seq:
                w(f"ALTER SEQUENCE public.{seq} OWNED BY public.{tbl}.{cname};")
                w(f"ALTER TABLE ONLY public.{tbl} ALTER COLUMN {cname} SET DEFAULT nextval('public.{seq}'::regclass);")
    w("")

# --- Donnees de reference ---
REF_TABLES = [
    "networks","roles","permissions","role_permissions","users",
    "bin_range","iso_field_catalog","message_types","campaigns","tests",
    "dmas_cards","dmas_kek","dmas_acq_keys","dmas_iss_keys",
    "swam_cards","swam_kek",
]
w("-- 5. Donnees de reference")
for tbl in REF_TABLES:
    headers, rows = qcsv(tbl)
    if not rows:
        w(f"-- {tbl} : vide")
        continue
    w(f"-- {tbl} ({len(rows)} lignes)")
    for row in rows:
        vals = ", ".join(ql(v) for v in row)
        cols = ", ".join(headers)
        w(f"INSERT INTO public.{tbl} ({cols}) VALUES ({vals});")
    w("")

# Sequences : reset apres inserts
w("-- 6. Reset sequences apres inserts")
for tbl in REF_TABLES:
    has_id = q(f"""
SELECT 1 FROM information_schema.columns
WHERE table_schema='public' AND table_name='{tbl}' AND column_name='id'
  AND column_default LIKE '%nextval%' LIMIT 1;
""")
    if has_id and has_id[0][0]:
        seq = q(f"""
SELECT pg_get_serial_sequence('public.{tbl}','id');
""")
        if seq and seq[0][0]:
            sname = seq[0][0]
            w(f"SELECT setval('{sname}', COALESCE((SELECT MAX(id) FROM public.{tbl}),1));")
w("")

# --- FK Constraints ---
w("-- 7. Contraintes FK")
fks = q("""
SELECT
    tc.table_name,
    tc.constraint_name,
    kcu.column_name,
    ccu.table_name  AS ref_table,
    ccu.column_name AS ref_col,
    rc.delete_rule,
    rc.update_rule
FROM information_schema.table_constraints AS tc
JOIN information_schema.key_column_usage AS kcu
  ON tc.constraint_name=kcu.constraint_name AND tc.table_schema=kcu.table_schema
JOIN information_schema.referential_constraints AS rc
  ON tc.constraint_name=rc.constraint_name
JOIN information_schema.constraint_column_usage AS ccu
  ON ccu.constraint_name=rc.unique_constraint_name AND ccu.table_schema=tc.table_schema
WHERE tc.table_schema='public' AND tc.constraint_type='FOREIGN KEY'
ORDER BY tc.table_name, tc.constraint_name;
""")
for row in fks:
    if not row[0]: continue
    tbl, cname, col, reftbl, refcol, del_rule, upd_rule = \
        row[0], row[1], row[2], row[3], row[4], row[5], row[6]
    stmt = f"ALTER TABLE ONLY public.{tbl} ADD CONSTRAINT {cname} FOREIGN KEY ({col}) REFERENCES public.{reftbl}({refcol})"
    if del_rule and del_rule != "NO ACTION":
        stmt += f" ON DELETE {del_rule}"
    if upd_rule and upd_rule != "NO ACTION":
        stmt += f" ON UPDATE {upd_rule}"
    w(stmt + ";")
w("")

# --- Index ---
w("-- 8. Index")
idxs = q("""
SELECT indexname, indexdef
FROM pg_indexes
WHERE schemaname='public'
  AND indexname NOT LIKE '%_pkey'
ORDER BY tablename, indexname;
""")
for row in idxs:
    if row[0] and row[1]:
        w(row[1] + ";")
w("")

# --- Controle ---
w("-- 9. Controle final")
w("SELECT 'tables total'   AS objet, count(*)::text AS n FROM pg_tables WHERE schemaname='public'")
w("UNION ALL SELECT 'swam_*',        count(*)::text FROM pg_tables WHERE tablename LIKE 'swam%'")
w("UNION ALL SELECT 'users (app)',   count(*)::text FROM users")
w("UNION ALL SELECT 'dmas_cards',    count(*)::text FROM dmas_cards")
w("UNION ALL SELECT 'swam_cards',    count(*)::text FROM swam_cards")
w("UNION ALL SELECT 'networks SWAM', COALESCE(issuer_iso_port::text,'NULL') FROM networks WHERE code='SWAM'")
w("ORDER BY objet;")

# Ecriture
with open(OUT, "w", encoding="utf-8", newline="\n") as f:
    f.write("\n".join(lines) + "\n")

size = Path(OUT).stat().st_size
print(f"\nFichier genere : {OUT}")
print(f"Taille         : {size//1024} Ko")
print(f"Lignes SQL     : {len(lines)}")
print("\nSur le PC cible :")
print('  PGPASSWORD=postgres123 psql.exe -U postgres -h localhost -f create-db.sql')
PYEOF
