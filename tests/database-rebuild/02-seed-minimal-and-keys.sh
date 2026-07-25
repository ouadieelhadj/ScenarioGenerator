#!/usr/bin/env bash
set -euo pipefail
set +H

# Paramétrage minimal, cartes de test, grants puis bootstrap des clés par API.
ROOT="${SG_ROOT:-D:/MoneyCore/ScenarioGenerator}"
PG_BIN="${PG_BIN:-D:/MoneyCore/PostgreSQL/18/bin}"
DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-5432}"
DB_NAME="${DB_NAME:-scenariogenerator}"
DB_ADMIN="${DB_ADMIN:-postgres}"

secret() {
  local name="$1" prompt="$2"
  if [ -z "${!name:-}" ]; then read -r -s -p "$prompt: " "$name"; echo; fi
  [ -n "${!name:-}" ] || { echo "$name requis"; exit 1; }
}
secret DB_PASSWORD "Mot de passe PostgreSQL administrateur"
secret ADMIN_BCRYPT "Hash BCrypt du compte admin"
export PGPASSWORD="$DB_PASSWORD"
PSQL=("$PG_BIN/psql.exe" -U "$DB_ADMIN" -h "$DB_HOST" -p "$DB_PORT"
      -d "$DB_NAME" -v ON_ERROR_STOP=1)

echo "[1/3] Paramétrage et cartes"
"${PSQL[@]}" -v admin_bcrypt="$ADMIN_BCRYPT" <<'SQL'
BEGIN;
TRUNCATE networks,mc_dmas_interface,swam_interface,roles,permissions,role_permissions,
         users,mc_dmas_cards,swam_cards RESTART IDENTITY CASCADE;

INSERT INTO networks
 (id,code,name,active,orchestrator_port,acquirer_rest_port,acquirer_jpos_port,
  issuer_rest_port,issuer_iso_port,acquirer_host,issuer_host)
VALUES
 (1,'DMAS','Mastercard DMAS',true,8080,8084,8600,8501,8500,'localhost','localhost'),
 (2,'SWAM','Switch Al Maghrib',true,8080,8094,8093,8511,8510,'localhost','localhost');

INSERT INTO mc_dmas_interface
 (id_interface,bank_code,label,acq_ica_de32,iss_ica_de100,fwd_id_de33,
  group_signon_de2,member_group_id,business_role,host,rest_port,target_host,
  target_port,status,active,iso_port,log_file)
VALUES
 ('DMAS_BANK_A','022905','Banque A','022905','022905','022905','40260',
  'TESTGRP01','BOTH','localhost',8084,'localhost',8500,'OFF',true,NULL,
  'D:/MoneyCore/ScenarioGenerator/logs/mc-dmas-member.log'),
 ('DMAS_MASTERCARD_1','002202','Mastercard DMAS 1',NULL,NULL,'002202',NULL,
  'TESTGRP01','BOTH','localhost',8501,NULL,NULL,'OFF',true,8500,
  'D:/MoneyCore/ScenarioGenerator/logs/mc-dmas-mastercard.log');

INSERT INTO swam_interface
 (id_interface,bank_code,label,acquirer_code_de32,issuer_code_de33,
  member_group_id,business_role,host,rest_port,iso_port,target_host,target_port,
  log_file,status,active)
VALUES
 ('SWAM_MEMBER_A','12345','Membre SWAM A','12345','300853','TESTGRP01',
  'ACQUIRER','localhost',8094,NULL,'localhost',8510,
  'D:/MoneyCore/ScenarioGenerator/logs/swam-acquirer.log','OFF',true),
 ('SWAM_NETWORK_1','300853','Reseau SWAM',NULL,'300853','TESTGRP01',
  'ISSUER','localhost',8511,8510,NULL,NULL,
  'D:/MoneyCore/ScenarioGenerator/logs/swam-issuer.log','OFF',true);

INSERT INTO roles(id,code,label) VALUES
 (1,'ADMIN','Administrateur'),(3,'OBSERVATEUR','Observateur'),
 (4,'EXPLOITATION','Exploitation');
INSERT INTO permissions(id,code,label,category) VALUES
 (1,'USER_MANAGE','Gérer les utilisateurs','GESTION'),
 (2,'ROLE_MANAGE','Gérer rôles et permissions','GESTION'),
 (3,'CATALOG_MANAGE','Gérer les catalogues','GESTION'),
 (4,'CAMPAIGN_VIEW','Consulter les campagnes','CAMPAGNE'),
 (5,'CAMPAIGN_CREATE','Créer une campagne','CAMPAGNE'),
 (6,'CAMPAIGN_GENERATE','Générer les transactions','CAMPAGNE'),
 (7,'CAMPAIGN_EXPORT','Exporter','CAMPAGNE'),
 (8,'CARD_PROVISION','Provisionner les cartes','ORCHESTRATION'),
 (9,'CAMPAIGN_REPLAY','Rejouer une campagne','ORCHESTRATION'),
 (10,'TPS_CREATE','Créer un test de charge','CHARGE'),
 (11,'TPS_RUN','Lancer une exécution','CHARGE'),
 (12,'EXECUTION_VIEW','Consulter les exécutions','CHARGE');
INSERT INTO role_permissions(permission_id,role_id)
SELECT id,1 FROM permissions;
INSERT INTO users(id,login,password,email,role,active,created_at,created_by)
VALUES(2,'admin',:'admin_bcrypt','admin@staging.com','ADMIN',true,now(),'system');

-- Profil exact du log Way4. 156 -> prochain ATC généré : 157 / 009D.
INSERT INTO mc_dmas_cards
 (id,pan,pin,balance,currency,expiry,status,bank_code,emv_aid,emv_aip,
  emv_psn,emv_atc,emv_app_version,emv_iad,emv_cvm_results,created_at,updated_at)
VALUES
 (1,'5413330002001049','1234',100000000,'504','2512','ACTIVE','022905',
  'A0000000041010','5800','00',156,'0001','0101A00000200000','020000',
  now(),now());

INSERT INTO swam_cards(id,pan,pin,balance,currency,expiry,status,created_at,updated_at)
VALUES(1,'5321962145453348','1234',100000000,'504','2812','ACTIVE',now(),now());
COMMIT;

GRANT USAGE ON SCHEMA public
 TO mc_dmas_member,mc_dmas_mastercard,swam_acquirer_user,swam_issuer_user;
GRANT SELECT,INSERT,UPDATE,DELETE ON ALL TABLES IN SCHEMA public
 TO mc_dmas_member,mc_dmas_mastercard,swam_acquirer_user,swam_issuer_user;
GRANT USAGE,SELECT,UPDATE ON ALL SEQUENCES IN SCHEMA public
 TO mc_dmas_member,mc_dmas_mastercard,swam_acquirer_user,swam_issuer_user;
SQL

echo "[2/3] Contrôle"
"${PSQL[@]}" -c "
SELECT code,acquirer_rest_port,issuer_rest_port,issuer_iso_port FROM networks;
SELECT id_interface,rest_port,iso_port,target_host,target_port FROM mc_dmas_interface;
SELECT pan,emv_psn,emv_atc,emv_iad FROM mc_dmas_cards;"

echo "[3/3] Bootstrap des clés sous les LMK locaux"
bash "$ROOT/tests/dmas/mastercard/start-and-bootstrap.sh"
bash "$ROOT/tests/dmas/member/start-and-bootstrap.sh"
bash "$ROOT/tests/swam/issuer/start-and-bootstrap.sh"
bash "$ROOT/tests/swam/acquirer/start-and-bootstrap.sh"
