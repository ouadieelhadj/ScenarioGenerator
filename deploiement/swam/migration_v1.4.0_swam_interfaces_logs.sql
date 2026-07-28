BEGIN;

ALTER TABLE IF EXISTS mc_dmas_interface
    ADD COLUMN IF NOT EXISTS log_file varchar(500);

CREATE TABLE IF NOT EXISTS swam_interface (
    id_interface varchar(32) PRIMARY KEY,
    bank_code varchar(20) NOT NULL,
    label varchar(100),
    acquirer_code_de32 varchar(20),
    issuer_code_de33 varchar(20),
    member_group_id varchar(32),
    business_role varchar(16),
    host varchar(100),
    rest_port integer,
    iso_port integer,
    target_host varchar(100),
    target_port integer,
    log_file varchar(500),
    status varchar(16),
    active boolean,
    created_at timestamp,
    updated_at timestamp
);

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
  'D:/MoneyCore/ScenarioGenerator/logs/swam-issuer.log','OFF',true)
ON CONFLICT (id_interface) DO UPDATE SET
 bank_code=EXCLUDED.bank_code,
 label=EXCLUDED.label,
 acquirer_code_de32=EXCLUDED.acquirer_code_de32,
 issuer_code_de33=EXCLUDED.issuer_code_de33,
 member_group_id=EXCLUDED.member_group_id,
 business_role=EXCLUDED.business_role,
 host=EXCLUDED.host,
 rest_port=EXCLUDED.rest_port,
 iso_port=EXCLUDED.iso_port,
 target_host=EXCLUDED.target_host,
 target_port=EXCLUDED.target_port,
 log_file=EXCLUDED.log_file,
 active=EXCLUDED.active;

DO $$
BEGIN
    IF to_regclass('public.mc_dmas_interface') IS NOT NULL THEN
        UPDATE mc_dmas_interface
        SET log_file = CASE id_interface
            WHEN 'DMAS_BANK_A'
                THEN 'D:/MoneyCore/ScenarioGenerator/logs/mc-dmas-member.log'
            WHEN 'DMAS_MASTERCARD_1'
                THEN 'D:/MoneyCore/ScenarioGenerator/logs/mc-dmas-mastercard.log'
            ELSE log_file
        END
        WHERE log_file IS NULL;
    END IF;
END $$;

GRANT SELECT,INSERT,UPDATE,DELETE ON swam_interface
 TO swam_acquirer_user,swam_issuer_user,mc_dmas_member,mc_dmas_mastercard;

COMMIT;
