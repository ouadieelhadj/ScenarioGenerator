-- ================================================================
-- create-db.sql — Base scenariogenerator COMPLETE
-- Genere par build-create-db.sh (aucune dependance externe).
-- Usage : psql -U postgres -f create-db.sql
-- ================================================================
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;

-- 1. Users applicatifs
DO $$
BEGIN
  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname='scenario_user')
    THEN CREATE ROLE scenario_user LOGIN PASSWORD 'postgres123'; END IF;
  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname='dmas_acquirer_user')
    THEN CREATE ROLE dmas_acquirer_user LOGIN PASSWORD 'postgres123'; END IF;
  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname='dmas_issuer_user')
    THEN CREATE ROLE dmas_issuer_user LOGIN PASSWORD 'postgres123'; END IF;
  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname='swam_issuer_user')
    THEN CREATE ROLE swam_issuer_user LOGIN PASSWORD 'postgres123'; END IF;
  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname='swam_acquirer_user')
    THEN CREATE ROLE swam_acquirer_user LOGIN PASSWORD 'postgres123'; END IF;
END $$;

-- 2. Base de donnees
DROP DATABASE IF EXISTS scenariogenerator;
CREATE DATABASE scenariogenerator OWNER scenario_user;
\connect scenariogenerator
GRANT ALL ON SCHEMA public TO scenario_user, dmas_acquirer_user, dmas_issuer_user, swam_issuer_user, swam_acquirer_user;

-- 3. Sequences
CREATE SEQUENCE public.acq_advices_id_seq
    START WITH 1
    INCREMENT BY 1
    MINVALUE 1
    NO MAXVALUE
    CACHE 1;
CREATE SEQUENCE public.acq_authorizations_id_seq
    START WITH 1
    INCREMENT BY 1
    MINVALUE 1
    NO MAXVALUE
    CACHE 1;
CREATE SEQUENCE public.acq_ipm_files_id_seq
    START WITH 1
    INCREMENT BY 1
    MINVALUE 1
    NO MAXVALUE
    CACHE 1;
CREATE SEQUENCE public.acq_ipm_records_id_seq
    START WITH 1
    INCREMENT BY 1
    MINVALUE 1
    NO MAXVALUE
    CACHE 1;
CREATE SEQUENCE public.acq_reversals_id_seq
    START WITH 1
    INCREMENT BY 1
    MINVALUE 1
    NO MAXVALUE
    CACHE 1;
CREATE SEQUENCE public.bin_range_id_seq
    START WITH 1
    INCREMENT BY 1
    MINVALUE 1
    NO MAXVALUE
    CACHE 1;
CREATE SEQUENCE public.campaign_execution_results_id_seq
    START WITH 1
    INCREMENT BY 1
    MINVALUE 1
    NO MAXVALUE
    CACHE 1;
CREATE SEQUENCE public.campaign_executions_id_seq
    START WITH 1
    INCREMENT BY 1
    MINVALUE 1
    NO MAXVALUE
    CACHE 1;
CREATE SEQUENCE public.campaign_load_steps_id_seq
    START WITH 1
    INCREMENT BY 1
    MINVALUE 1
    NO MAXVALUE
    CACHE 1;
CREATE SEQUENCE public.campaigns_id_seq
    START WITH 1
    INCREMENT BY 1
    MINVALUE 1
    NO MAXVALUE
    CACHE 1;
CREATE SEQUENCE public.dmas_acq_keys_id_seq
    START WITH 1
    INCREMENT BY 1
    MINVALUE 1
    NO MAXVALUE
    CACHE 1;
CREATE SEQUENCE public.dmas_cards_id_seq
    START WITH 1
    INCREMENT BY 1
    MINVALUE 1
    NO MAXVALUE
    CACHE 1;
CREATE SEQUENCE public.dmas_iss_keys_id_seq
    START WITH 1
    INCREMENT BY 1
    MINVALUE 1
    NO MAXVALUE
    CACHE 1;
CREATE SEQUENCE public.dmas_kek_id_seq
    START WITH 1
    INCREMENT BY 1
    MINVALUE 1
    NO MAXVALUE
    CACHE 1;
CREATE SEQUENCE public.dmas_transactions_id_seq
    START WITH 1
    INCREMENT BY 1
    MINVALUE 1
    NO MAXVALUE
    CACHE 1;
CREATE SEQUENCE public.executions_id_seq
    START WITH 1
    INCREMENT BY 1
    MINVALUE 1
    NO MAXVALUE
    CACHE 1;
CREATE SEQUENCE public.ipm_files_id_seq
    START WITH 1
    INCREMENT BY 1
    MINVALUE 1
    NO MAXVALUE
    CACHE 1;
CREATE SEQUENCE public.ipm_processing_log_id_seq
    START WITH 1
    INCREMENT BY 1
    MINVALUE 1
    NO MAXVALUE
    CACHE 1;
CREATE SEQUENCE public.ipm_records_id_seq
    START WITH 1
    INCREMENT BY 1
    MINVALUE 1
    NO MAXVALUE
    CACHE 1;
CREATE SEQUENCE public.iso_field_catalog_id_seq
    START WITH 1
    INCREMENT BY 1
    MINVALUE 1
    NO MAXVALUE
    CACHE 1;
CREATE SEQUENCE public.iss_advices_id_seq
    START WITH 1
    INCREMENT BY 1
    MINVALUE 1
    NO MAXVALUE
    CACHE 1;
CREATE SEQUENCE public.iss_authorizations_id_seq
    START WITH 1
    INCREMENT BY 1
    MINVALUE 1
    NO MAXVALUE
    CACHE 1;
CREATE SEQUENCE public.iss_ipm_files_id_seq
    START WITH 1
    INCREMENT BY 1
    MINVALUE 1
    NO MAXVALUE
    CACHE 1;
CREATE SEQUENCE public.iss_ipm_records_id_seq
    START WITH 1
    INCREMENT BY 1
    MINVALUE 1
    NO MAXVALUE
    CACHE 1;
CREATE SEQUENCE public.iss_reversals_id_seq
    START WITH 1
    INCREMENT BY 1
    MINVALUE 1
    NO MAXVALUE
    CACHE 1;
CREATE SEQUENCE public.key_store_id_seq
    START WITH 1
    INCREMENT BY 1
    MINVALUE 1
    NO MAXVALUE
    CACHE 1;
CREATE SEQUENCE public.message_types_id_seq
    START WITH 1
    INCREMENT BY 1
    MINVALUE 1
    NO MAXVALUE
    CACHE 1;
CREATE SEQUENCE public.networks_id_seq
    START WITH 1
    INCREMENT BY 1
    MINVALUE 1
    NO MAXVALUE
    CACHE 1;
CREATE SEQUENCE public.permissions_id_seq
    START WITH 1
    INCREMENT BY 1
    MINVALUE 1
    NO MAXVALUE
    CACHE 1;
CREATE SEQUENCE public.results_id_seq
    START WITH 1
    INCREMENT BY 1
    MINVALUE 1
    NO MAXVALUE
    CACHE 1;
CREATE SEQUENCE public.roles_id_seq
    START WITH 1
    INCREMENT BY 1
    MINVALUE 1
    NO MAXVALUE
    CACHE 1;
CREATE SEQUENCE public.swam_acq_keys_id_seq
    START WITH 1
    INCREMENT BY 1
    MINVALUE 1
    NO MAXVALUE
    CACHE 1;
CREATE SEQUENCE public.swam_acq_transactions_id_seq
    START WITH 1
    INCREMENT BY 1
    MINVALUE 1
    NO MAXVALUE
    CACHE 1;
CREATE SEQUENCE public.swam_cards_id_seq
    START WITH 1
    INCREMENT BY 1
    MINVALUE 1
    NO MAXVALUE
    CACHE 1;
CREATE SEQUENCE public.swam_iss_keys_id_seq
    START WITH 1
    INCREMENT BY 1
    MINVALUE 1
    NO MAXVALUE
    CACHE 1;
CREATE SEQUENCE public.swam_iss_transactions_id_seq
    START WITH 1
    INCREMENT BY 1
    MINVALUE 1
    NO MAXVALUE
    CACHE 1;
CREATE SEQUENCE public.swam_kek_id_seq
    START WITH 1
    INCREMENT BY 1
    MINVALUE 1
    NO MAXVALUE
    CACHE 1;
CREATE SEQUENCE public.tests_id_seq
    START WITH 1
    INCREMENT BY 1
    MINVALUE 1
    NO MAXVALUE
    CACHE 1;
CREATE SEQUENCE public.tps_steps_id_seq
    START WITH 1
    INCREMENT BY 1
    MINVALUE 1
    NO MAXVALUE
    CACHE 1;
CREATE SEQUENCE public.users_id_seq
    START WITH 1
    INCREMENT BY 1
    MINVALUE 1
    NO MAXVALUE
    CACHE 1;

-- 4. Tables (sans contraintes FK)
CREATE TABLE public.acq_advices (
    id bigint DEFAULT nextval('acq_advices_id_seq'::regclass) NOT NULL,
    execution_id bigint,
    acq_auth_id bigint,
    de002_pan character varying(20),
    de003_proc_code character varying(6),
    de004_amount bigint,
    de007_datetime character varying(10),
    de011_stan character varying(6),
    de037_rrn character varying(12),
    de038_auth_code character varying(6),
    de039_response character varying(2),
    de049_currency character varying(3),
    de060_reason character varying(3),
    de039_advice_response character varying(2),
    accepted boolean,
    duration_ms integer,
    request_hex text,
    response_hex text,
    sent_at timestamp without time zone DEFAULT now(),
    CONSTRAINT acq_advices_pkey PRIMARY KEY (id)
);
ALTER SEQUENCE public.acq_advices_id_seq OWNED BY public.acq_advices.id;
ALTER TABLE ONLY public.acq_advices ALTER COLUMN id SET DEFAULT nextval('public.acq_advices_id_seq'::regclass);

CREATE TABLE public.acq_authorizations (
    id bigint DEFAULT nextval('acq_authorizations_id_seq'::regclass) NOT NULL,
    execution_id bigint,
    de002_pan character varying(20),
    de003_proc_code character varying(6),
    de004_amount bigint,
    de007_datetime character varying(10),
    de011_stan character varying(6),
    de012_local_time character varying(6),
    de013_local_date character varying(4),
    de018_mcc character varying(4),
    de022_pos_mode character varying(3),
    de032_acq_id character varying(11),
    de037_rrn character varying(12),
    de041_term_id character varying(8),
    de042_merch_id character varying(15),
    de043_merch_name character varying(40),
    de049_currency character varying(3),
    de052_pin_present boolean DEFAULT false,
    de038_auth_code character varying(6),
    de039_response character varying(2),
    approved boolean,
    duration_ms integer,
    request_hex text,
    response_hex text,
    sent_at timestamp without time zone DEFAULT now(),
    de002_pan_raw character varying(20),
    ipm_generated boolean DEFAULT false,
    ipm_file_id bigint,
    ipm_file_name character varying(100),
    ipm_generated_at timestamp without time zone,
    CONSTRAINT acq_authorizations_pkey PRIMARY KEY (id)
);
ALTER SEQUENCE public.acq_authorizations_id_seq OWNED BY public.acq_authorizations.id;
ALTER TABLE ONLY public.acq_authorizations ALTER COLUMN id SET DEFAULT nextval('public.acq_authorizations_id_seq'::regclass);

CREATE TABLE public.acq_ipm_files (
    id bigint DEFAULT nextval('acq_ipm_files_id_seq'::regclass) NOT NULL,
    file_name character varying(100) NOT NULL,
    file_path_binary character varying(500),
    file_path_ascii character varying(500),
    file_date date NOT NULL,
    generation_date timestamp without time zone DEFAULT now(),
    status character varying(20) DEFAULT 'GENERATED'::character varying,
    direction character varying(3) DEFAULT 'OUT'::character varying,
    nb_transactions integer DEFAULT 0,
    total_amount bigint DEFAULT 0,
    total_amount_currency character varying(3),
    file_id character varying(50),
    processing_mode character varying(10) DEFAULT 'TEST'::character varying,
    execution_id bigint,
    created_at timestamp without time zone DEFAULT now(),
    created_by character varying(50),
    CONSTRAINT acq_ipm_files_pkey PRIMARY KEY (id)
);
ALTER SEQUENCE public.acq_ipm_files_id_seq OWNED BY public.acq_ipm_files.id;
ALTER TABLE ONLY public.acq_ipm_files ALTER COLUMN id SET DEFAULT nextval('public.acq_ipm_files_id_seq'::regclass);

CREATE TABLE public.acq_ipm_records (
    id bigint DEFAULT nextval('acq_ipm_records_id_seq'::regclass) NOT NULL,
    ipm_file_id bigint NOT NULL,
    acq_auth_id bigint,
    direction character varying(3) DEFAULT 'OUT'::character varying,
    message_number integer NOT NULL,
    record_type character varying(15) NOT NULL,
    mti character varying(4) NOT NULL,
    function_code character varying(3),
    de002_pan character varying(20),
    de003_proc_code character varying(6),
    de004_amount bigint,
    de005_amount_recon bigint,
    de012_local_dt character varying(12),
    de022_pos_code character varying(12),
    de024_func_code character varying(3),
    de025_reason character varying(4),
    de026_mcc character varying(4),
    de030_orig_amount bigint,
    de031_acq_ref_data character varying(23),
    de032_acq_id character varying(11),
    de037_rrn character varying(12),
    de038_auth_code character varying(6),
    de041_term_id character varying(8),
    de042_merch_id character varying(15),
    de043_merch_name character varying(40),
    de049_currency character varying(3),
    de050_currency_recon character varying(3),
    de063_network_data character varying(50),
    de071_msg_num character varying(8),
    de072_data_record character varying(255),
    pds_data text,
    raw_hex text,
    raw_ascii text,
    status character varying(10) DEFAULT 'OK'::character varying,
    error_message character varying(255),
    created_at timestamp without time zone DEFAULT now(),
    CONSTRAINT acq_ipm_records_pkey PRIMARY KEY (id)
);
ALTER SEQUENCE public.acq_ipm_records_id_seq OWNED BY public.acq_ipm_records.id;
ALTER TABLE ONLY public.acq_ipm_records ALTER COLUMN id SET DEFAULT nextval('public.acq_ipm_records_id_seq'::regclass);

CREATE TABLE public.acq_reversals (
    id bigint DEFAULT nextval('acq_reversals_id_seq'::regclass) NOT NULL,
    execution_id bigint,
    acq_auth_id bigint,
    de002_pan character varying(20),
    de003_proc_code character varying(6),
    de004_amount bigint,
    de007_datetime character varying(10),
    de011_stan character varying(6),
    de037_rrn character varying(12),
    de038_auth_code character varying(6),
    de039_original character varying(2),
    de041_term_id character varying(8),
    de042_merch_id character varying(15),
    de049_currency character varying(3),
    de056_orig_data character varying(40),
    de039_response character varying(2),
    reversed boolean,
    duration_ms integer,
    request_hex text,
    response_hex text,
    sent_at timestamp without time zone DEFAULT now(),
    CONSTRAINT acq_reversals_pkey PRIMARY KEY (id)
);
ALTER SEQUENCE public.acq_reversals_id_seq OWNED BY public.acq_reversals.id;
ALTER TABLE ONLY public.acq_reversals ALTER COLUMN id SET DEFAULT nextval('public.acq_reversals_id_seq'::regclass);

CREATE TABLE public.bin_range (
    id bigint DEFAULT nextval('bin_range_id_seq'::regclass) NOT NULL,
    code character varying(20) NOT NULL,
    product_name character varying(60) NOT NULL,
    network character varying(20) DEFAULT 'MASTERCARD'::character varying NOT NULL,
    pan_length integer DEFAULT 16 NOT NULL,
    is_range boolean DEFAULT false NOT NULL,
    enabled boolean DEFAULT true NOT NULL,
    CONSTRAINT bin_range_pkey PRIMARY KEY (id)
);
ALTER SEQUENCE public.bin_range_id_seq OWNED BY public.bin_range.id;
ALTER TABLE ONLY public.bin_range ALTER COLUMN id SET DEFAULT nextval('public.bin_range_id_seq'::regclass);

CREATE TABLE public.campaign_execution_results (
    id bigint DEFAULT nextval('campaign_execution_results_id_seq'::regclass) NOT NULL,
    execution_id bigint NOT NULL,
    step_order integer,
    pan_masked character varying(20),
    de039 character varying(2),
    de038_auth_code character varying(6),
    approved boolean,
    duration_ms integer,
    request_hex text,
    response_hex text,
    executed_at timestamp without time zone DEFAULT now(),
    CONSTRAINT campaign_execution_results_pkey PRIMARY KEY (id)
);
ALTER SEQUENCE public.campaign_execution_results_id_seq OWNED BY public.campaign_execution_results.id;
ALTER TABLE ONLY public.campaign_execution_results ALTER COLUMN id SET DEFAULT nextval('public.campaign_execution_results_id_seq'::regclass);

CREATE TABLE public.campaign_executions (
    id bigint DEFAULT nextval('campaign_executions_id_seq'::regclass) NOT NULL,
    campaign_id bigint NOT NULL,
    user_id bigint NOT NULL,
    status character varying(20) NOT NULL,
    tps_target integer,
    duration_seconds integer,
    tx_total integer DEFAULT 0,
    tx_sent integer DEFAULT 0,
    tx_approved integer DEFAULT 0,
    tx_declined integer DEFAULT 0,
    tps_actual_avg numeric(10,2),
    response_time_avg numeric(10,2),
    response_time_min numeric(10,2),
    response_time_max numeric(10,2),
    response_time_p95 numeric(10,2),
    response_time_p99 numeric(10,2),
    verdict character varying(10),
    verdict_detail character varying(255),
    started_at timestamp without time zone DEFAULT now(),
    ended_at timestamp without time zone,
    report_dir character varying(255),
    report_pdf character varying(255),
    report_excel character varying(255),
    CONSTRAINT campaign_executions_pkey PRIMARY KEY (id)
);
ALTER SEQUENCE public.campaign_executions_id_seq OWNED BY public.campaign_executions.id;
ALTER TABLE ONLY public.campaign_executions ALTER COLUMN id SET DEFAULT nextval('public.campaign_executions_id_seq'::regclass);

CREATE TABLE public.campaign_load_steps (
    id bigint DEFAULT nextval('campaign_load_steps_id_seq'::regclass) NOT NULL,
    campaign_id bigint NOT NULL,
    step_order integer NOT NULL,
    start_seconds integer NOT NULL,
    end_seconds integer NOT NULL,
    tps_value integer NOT NULL,
    concurrency integer,
    CONSTRAINT campaign_load_steps_pkey PRIMARY KEY (id)
);
ALTER SEQUENCE public.campaign_load_steps_id_seq OWNED BY public.campaign_load_steps.id;
ALTER TABLE ONLY public.campaign_load_steps ALTER COLUMN id SET DEFAULT nextval('public.campaign_load_steps_id_seq'::regclass);

CREATE TABLE public.campaigns (
    id bigint DEFAULT nextval('campaigns_id_seq'::regclass) NOT NULL,
    name character varying(100) NOT NULL,
    description character varying(255),
    category character varying(50),
    config text,
    expected_de039 character varying(2),
    active boolean DEFAULT true,
    created_at timestamp without time zone DEFAULT now(),
    created_by bigint,
    sla_p95_max_ms integer,
    sla_error_rate_max numeric(5,2),
    sla_approval_min numeric(5,2),
    stop_on_error_rate numeric(5,2),
    network character varying(20) DEFAULT 'DMAS'::character varying NOT NULL,
    initiator character varying(20) DEFAULT 'ACQUIRER'::character varying NOT NULL,
    CONSTRAINT campaigns_pkey PRIMARY KEY (id)
);
ALTER SEQUENCE public.campaigns_id_seq OWNED BY public.campaigns.id;
ALTER TABLE ONLY public.campaigns ALTER COLUMN id SET DEFAULT nextval('public.campaigns_id_seq'::regclass);

CREATE TABLE public.databasechangelog (
    id character varying(255) NOT NULL,
    author character varying(255) NOT NULL,
    filename character varying(255) NOT NULL,
    dateexecuted timestamp without time zone NOT NULL,
    orderexecuted integer NOT NULL,
    exectype character varying(10) NOT NULL,
    md5sum character varying(35),
    description character varying(255),
    comments character varying(255),
    tag character varying(255),
    liquibase character varying(20),
    contexts character varying(255),
    labels character varying(255),
    deployment_id character varying(10)
);

CREATE TABLE public.databasechangeloglock (
    id integer NOT NULL,
    locked boolean NOT NULL,
    lockgranted timestamp without time zone,
    lockedby character varying(255),
    CONSTRAINT databasechangeloglock_pkey PRIMARY KEY (id)
);

CREATE TABLE public.dmas_acq_keys (
    id bigint DEFAULT nextval('dmas_acq_keys_id_seq'::regclass) NOT NULL,
    member_group_id character varying(20) NOT NULL,
    key_type character varying(3) NOT NULL,
    key_length integer DEFAULT 24 NOT NULL,
    key_under_lmk character varying(64),
    key_under_kek character varying(64),
    kcv character varying(6),
    status character varying(10) DEFAULT 'ACTIVE'::character varying NOT NULL,
    created_at timestamp without time zone DEFAULT now(),
    CONSTRAINT dmas_acq_keys_pkey PRIMARY KEY (id),
    CONSTRAINT uq_dmas_acq_keys UNIQUE (member_group_id, key_type, status)
);
ALTER SEQUENCE public.dmas_acq_keys_id_seq OWNED BY public.dmas_acq_keys.id;
ALTER TABLE ONLY public.dmas_acq_keys ALTER COLUMN id SET DEFAULT nextval('public.dmas_acq_keys_id_seq'::regclass);

CREATE TABLE public.dmas_cards (
    id bigint DEFAULT nextval('dmas_cards_id_seq'::regclass) NOT NULL,
    pan character varying(19) NOT NULL,
    pin character varying(12) NOT NULL,
    balance bigint DEFAULT 0 NOT NULL,
    currency character varying(3) DEFAULT '840'::character varying NOT NULL,
    expiry character varying(4),
    status character varying(10) DEFAULT 'ACTIVE'::character varying NOT NULL,
    created_at timestamp without time zone DEFAULT now(),
    updated_at timestamp without time zone DEFAULT now(),
    CONSTRAINT dmas_cards_pkey PRIMARY KEY (id),
    CONSTRAINT dmas_cards_pan_key UNIQUE (pan)
);
ALTER SEQUENCE public.dmas_cards_id_seq OWNED BY public.dmas_cards.id;
ALTER TABLE ONLY public.dmas_cards ALTER COLUMN id SET DEFAULT nextval('public.dmas_cards_id_seq'::regclass);

CREATE TABLE public.dmas_iss_keys (
    id bigint DEFAULT nextval('dmas_iss_keys_id_seq'::regclass) NOT NULL,
    member_group_id character varying(20) NOT NULL,
    key_type character varying(3) NOT NULL,
    key_length integer DEFAULT 24 NOT NULL,
    key_under_lmk character varying(64),
    key_under_kek character varying(64),
    kcv character varying(6),
    status character varying(10) DEFAULT 'ACTIVE'::character varying NOT NULL,
    created_at timestamp without time zone DEFAULT now(),
    CONSTRAINT dmas_iss_keys_pkey PRIMARY KEY (id),
    CONSTRAINT uq_dmas_iss_keys UNIQUE (member_group_id, key_type, status)
);
ALTER SEQUENCE public.dmas_iss_keys_id_seq OWNED BY public.dmas_iss_keys.id;
ALTER TABLE ONLY public.dmas_iss_keys ALTER COLUMN id SET DEFAULT nextval('public.dmas_iss_keys_id_seq'::regclass);

CREATE TABLE public.dmas_kek (
    id bigint DEFAULT nextval('dmas_kek_id_seq'::regclass) NOT NULL,
    member_group_id character varying(20) NOT NULL,
    key_length integer DEFAULT 24 NOT NULL,
    kek_clear character varying(48),
    kek_under_acq_lmk character varying(128),
    kek_under_iss_lmk character varying(128),
    kcv character varying(6),
    status character varying(10) DEFAULT 'ACTIVE'::character varying NOT NULL,
    description character varying(255),
    created_at timestamp without time zone DEFAULT now(),
    CONSTRAINT dmas_kek_pkey PRIMARY KEY (id),
    CONSTRAINT uq_dmas_kek_group UNIQUE (member_group_id)
);
ALTER SEQUENCE public.dmas_kek_id_seq OWNED BY public.dmas_kek.id;
ALTER TABLE ONLY public.dmas_kek ALTER COLUMN id SET DEFAULT nextval('public.dmas_kek_id_seq'::regclass);

CREATE TABLE public.dmas_transactions (
    id bigint DEFAULT nextval('dmas_transactions_id_seq'::regclass) NOT NULL,
    pan character varying(19) NOT NULL,
    stan character varying(6) NOT NULL,
    transmission_dt character varying(10) NOT NULL,
    mti character varying(4) NOT NULL,
    processing_code character varying(6),
    amount bigint NOT NULL,
    currency character varying(3),
    response_code character varying(2),
    status character varying(10) DEFAULT 'APPROVED'::character varying NOT NULL,
    created_at timestamp without time zone DEFAULT now(),
    reversed_at timestamp without time zone,
    CONSTRAINT dmas_transactions_pkey PRIMARY KEY (id),
    CONSTRAINT uq_dmas_tx UNIQUE (stan, transmission_dt)
);
ALTER SEQUENCE public.dmas_transactions_id_seq OWNED BY public.dmas_transactions.id;
ALTER TABLE ONLY public.dmas_transactions ALTER COLUMN id SET DEFAULT nextval('public.dmas_transactions_id_seq'::regclass);

CREATE TABLE public.executions (
    id bigint DEFAULT nextval('executions_id_seq'::regclass) NOT NULL,
    user_id bigint NOT NULL,
    test_id bigint NOT NULL,
    mode character varying(20) NOT NULL,
    status character varying(20) NOT NULL,
    tps_target integer,
    duration_seconds integer,
    tx_total integer DEFAULT 0,
    tx_sent integer DEFAULT 0,
    tx_approved integer DEFAULT 0,
    tx_declined integer DEFAULT 0,
    tps_actual_avg numeric(10,2),
    response_time_avg numeric(10,2),
    response_time_min numeric(10,2),
    response_time_max numeric(10,2),
    response_time_p95 numeric(10,2),
    response_time_p99 numeric(10,2),
    started_at timestamp without time zone DEFAULT now(),
    ended_at timestamp without time zone,
    report_dir character varying(255),
    report_pdf character varying(255),
    report_excel character varying(255),
    CONSTRAINT executions_pkey PRIMARY KEY (id)
);
ALTER SEQUENCE public.executions_id_seq OWNED BY public.executions.id;
ALTER TABLE ONLY public.executions ALTER COLUMN id SET DEFAULT nextval('public.executions_id_seq'::regclass);

CREATE TABLE public.ipm_files (
    id bigint DEFAULT nextval('ipm_files_id_seq'::regclass) NOT NULL,
    file_name character varying(100) NOT NULL,
    file_path_binary character varying(500),
    file_path_ascii character varying(500),
    file_date date NOT NULL,
    generation_date timestamp without time zone DEFAULT now(),
    status character varying(20) DEFAULT 'GENERATED'::character varying,
    nb_transactions integer DEFAULT 0,
    total_amount bigint DEFAULT 0,
    total_amount_currency character varying(3),
    file_id character varying(50),
    processing_mode character varying(10) DEFAULT 'TEST'::character varying,
    execution_id bigint,
    created_at timestamp without time zone DEFAULT now(),
    created_by character varying(50),
    CONSTRAINT ipm_files_pkey PRIMARY KEY (id)
);
ALTER SEQUENCE public.ipm_files_id_seq OWNED BY public.ipm_files.id;
ALTER TABLE ONLY public.ipm_files ALTER COLUMN id SET DEFAULT nextval('public.ipm_files_id_seq'::regclass);

CREATE TABLE public.ipm_processing_log (
    id bigint DEFAULT nextval('ipm_processing_log_id_seq'::regclass) NOT NULL,
    file_id character varying(50),
    file_name character varying(100),
    file_path character varying(500),
    role character varying(10),
    direction character varying(3),
    action character varying(15),
    execution_id bigint,
    record_count integer,
    checksum character varying(64),
    status character varying(15),
    processed_at timestamp without time zone DEFAULT now(),
    CONSTRAINT ipm_processing_log_pkey PRIMARY KEY (id),
    CONSTRAINT uq_ipm_log UNIQUE (file_name, role, direction)
);
ALTER SEQUENCE public.ipm_processing_log_id_seq OWNED BY public.ipm_processing_log.id;
ALTER TABLE ONLY public.ipm_processing_log ALTER COLUMN id SET DEFAULT nextval('public.ipm_processing_log_id_seq'::regclass);

CREATE TABLE public.ipm_records (
    id bigint DEFAULT nextval('ipm_records_id_seq'::regclass) NOT NULL,
    ipm_file_id bigint NOT NULL,
    acq_auth_id bigint,
    message_number integer NOT NULL,
    record_type character varying(15) NOT NULL,
    mti character varying(4) NOT NULL,
    function_code character varying(3),
    de002_pan character varying(20),
    de003_proc_code character varying(6),
    de004_amount bigint,
    de012_local_dt character varying(12),
    de022_pos_code character varying(12),
    de024_func_code character varying(3),
    de025_reason character varying(4),
    de026_mcc character varying(4),
    de032_acq_id character varying(11),
    de037_rrn character varying(12),
    de038_auth_code character varying(6),
    de041_term_id character varying(8),
    de042_merch_id character varying(15),
    de043_merch_name character varying(40),
    de049_currency character varying(3),
    de071_msg_num character varying(8),
    raw_hex text,
    raw_ascii text,
    status character varying(10) DEFAULT 'OK'::character varying,
    error_message character varying(255),
    created_at timestamp without time zone DEFAULT now(),
    de005_amount_recon bigint,
    de031_acq_ref_data character varying(23),
    de050_currency_recon character varying(3),
    de063_network_data character varying(50),
    CONSTRAINT ipm_records_pkey PRIMARY KEY (id)
);
ALTER SEQUENCE public.ipm_records_id_seq OWNED BY public.ipm_records.id;
ALTER TABLE ONLY public.ipm_records ALTER COLUMN id SET DEFAULT nextval('public.ipm_records_id_seq'::regclass);

CREATE TABLE public.iso_field_catalog (
    id bigint DEFAULT nextval('iso_field_catalog_id_seq'::regclass) NOT NULL,
    field_code character varying(10) NOT NULL,
    name character varying(60) NOT NULL,
    description character varying(255),
    gen_strategy character varying(40) NOT NULL,
    enabled boolean DEFAULT true NOT NULL,
    display_order integer DEFAULT 0 NOT NULL,
    CONSTRAINT iso_field_catalog_pkey PRIMARY KEY (id),
    CONSTRAINT iso_field_catalog_field_code_key UNIQUE (field_code)
);
ALTER SEQUENCE public.iso_field_catalog_id_seq OWNED BY public.iso_field_catalog.id;
ALTER TABLE ONLY public.iso_field_catalog ALTER COLUMN id SET DEFAULT nextval('public.iso_field_catalog_id_seq'::regclass);

CREATE TABLE public.iss_advices (
    id bigint DEFAULT nextval('iss_advices_id_seq'::regclass) NOT NULL,
    iss_auth_id bigint,
    de002_pan character varying(20),
    de003_proc_code character varying(6),
    de004_amount bigint,
    de007_datetime character varying(10),
    de011_stan character varying(6),
    de037_rrn character varying(12),
    de038_auth_code character varying(6),
    de039_response character varying(2),
    de049_currency character varying(3),
    de060_reason character varying(3),
    de039_advice_response character varying(2),
    accepted boolean,
    request_hex text,
    response_hex text,
    received_at timestamp without time zone DEFAULT now(),
    CONSTRAINT iss_advices_pkey PRIMARY KEY (id)
);
ALTER SEQUENCE public.iss_advices_id_seq OWNED BY public.iss_advices.id;
ALTER TABLE ONLY public.iss_advices ALTER COLUMN id SET DEFAULT nextval('public.iss_advices_id_seq'::regclass);

CREATE TABLE public.iss_authorizations (
    id bigint DEFAULT nextval('iss_authorizations_id_seq'::regclass) NOT NULL,
    de002_pan character varying(20),
    de003_proc_code character varying(6),
    de004_amount bigint,
    de007_datetime character varying(10),
    de011_stan character varying(6),
    de012_local_time character varying(6),
    de013_local_date character varying(4),
    de018_mcc character varying(4),
    de022_pos_mode character varying(3),
    de032_acq_id character varying(11),
    de037_rrn character varying(12),
    de041_term_id character varying(8),
    de042_merch_id character varying(15),
    de043_merch_name character varying(40),
    de049_currency character varying(3),
    de052_pin_present boolean DEFAULT false,
    mac_verified boolean DEFAULT false,
    de038_auth_code character varying(6),
    de039_response character varying(2),
    decision_reason character varying(100),
    approved boolean,
    request_hex text,
    response_hex text,
    received_at timestamp without time zone DEFAULT now(),
    responded_at timestamp without time zone,
    ipm_generated boolean DEFAULT false,
    ipm_file_id bigint,
    ipm_file_name character varying(100),
    ipm_generated_at timestamp without time zone,
    de002_pan_raw character varying(19),
    CONSTRAINT iss_authorizations_pkey PRIMARY KEY (id)
);
ALTER SEQUENCE public.iss_authorizations_id_seq OWNED BY public.iss_authorizations.id;
ALTER TABLE ONLY public.iss_authorizations ALTER COLUMN id SET DEFAULT nextval('public.iss_authorizations_id_seq'::regclass);

CREATE TABLE public.iss_ipm_files (
    id bigint DEFAULT nextval('iss_ipm_files_id_seq'::regclass) NOT NULL,
    file_name character varying(100) NOT NULL,
    file_path_binary character varying(500),
    file_path_ascii character varying(500),
    file_date date NOT NULL,
    generation_date timestamp without time zone DEFAULT now(),
    status character varying(20) DEFAULT 'GENERATED'::character varying,
    direction character varying(3) DEFAULT 'OUT'::character varying,
    nb_transactions integer DEFAULT 0,
    total_amount bigint DEFAULT 0,
    total_amount_currency character varying(3),
    file_id character varying(50),
    processing_mode character varying(10) DEFAULT 'TEST'::character varying,
    execution_id bigint,
    created_at timestamp without time zone DEFAULT now(),
    created_by character varying(50),
    CONSTRAINT iss_ipm_files_pkey PRIMARY KEY (id)
);
ALTER SEQUENCE public.iss_ipm_files_id_seq OWNED BY public.iss_ipm_files.id;
ALTER TABLE ONLY public.iss_ipm_files ALTER COLUMN id SET DEFAULT nextval('public.iss_ipm_files_id_seq'::regclass);

CREATE TABLE public.iss_ipm_records (
    id bigint DEFAULT nextval('iss_ipm_records_id_seq'::regclass) NOT NULL,
    ipm_file_id bigint NOT NULL,
    iss_auth_id bigint,
    direction character varying(3) DEFAULT 'OUT'::character varying,
    message_number integer NOT NULL,
    record_type character varying(15) NOT NULL,
    mti character varying(4) NOT NULL,
    function_code character varying(3),
    de002_pan character varying(20),
    de003_proc_code character varying(6),
    de004_amount bigint,
    de005_amount_recon bigint,
    de012_local_dt character varying(12),
    de022_pos_code character varying(12),
    de024_func_code character varying(3),
    de025_reason character varying(4),
    de026_mcc character varying(4),
    de030_orig_amount bigint,
    de031_acq_ref_data character varying(23),
    de032_acq_id character varying(11),
    de037_rrn character varying(12),
    de038_auth_code character varying(6),
    de041_term_id character varying(8),
    de042_merch_id character varying(15),
    de043_merch_name character varying(40),
    de049_currency character varying(3),
    de050_currency_recon character varying(3),
    de063_network_data character varying(50),
    de071_msg_num character varying(8),
    de072_data_record character varying(255),
    de093_dest_id character varying(11),
    de094_origin_id character varying(11),
    pds_data text,
    raw_hex text,
    raw_ascii text,
    status character varying(10) DEFAULT 'OK'::character varying,
    error_message character varying(255),
    created_at timestamp without time zone DEFAULT now(),
    CONSTRAINT iss_ipm_records_pkey PRIMARY KEY (id)
);
ALTER SEQUENCE public.iss_ipm_records_id_seq OWNED BY public.iss_ipm_records.id;
ALTER TABLE ONLY public.iss_ipm_records ALTER COLUMN id SET DEFAULT nextval('public.iss_ipm_records_id_seq'::regclass);

CREATE TABLE public.iss_reversals (
    id bigint DEFAULT nextval('iss_reversals_id_seq'::regclass) NOT NULL,
    iss_auth_id bigint,
    de002_pan character varying(20),
    de003_proc_code character varying(6),
    de004_amount bigint,
    de007_datetime character varying(10),
    de011_stan character varying(6),
    de037_rrn character varying(12),
    de038_auth_code character varying(6),
    de039_original character varying(2),
    de041_term_id character varying(8),
    de049_currency character varying(3),
    de056_orig_data character varying(40),
    de039_response character varying(2),
    reversed boolean,
    request_hex text,
    response_hex text,
    received_at timestamp without time zone DEFAULT now(),
    CONSTRAINT iss_reversals_pkey PRIMARY KEY (id)
);
ALTER SEQUENCE public.iss_reversals_id_seq OWNED BY public.iss_reversals.id;
ALTER TABLE ONLY public.iss_reversals ALTER COLUMN id SET DEFAULT nextval('public.iss_reversals_id_seq'::regclass);

CREATE TABLE public.key_store (
    id bigint DEFAULT nextval('key_store_id_seq'::regclass) NOT NULL,
    member_group_id character varying(20) NOT NULL,
    key_type character varying(3) NOT NULL,
    key_length integer DEFAULT 24 NOT NULL,
    encrypted_value character varying(64) NOT NULL,
    kcv character varying(6) NOT NULL,
    status character varying(10) DEFAULT 'ACTIVE'::character varying NOT NULL,
    description character varying(255),
    created_at timestamp without time zone DEFAULT now(),
    activated_at timestamp without time zone,
    CONSTRAINT key_store_pkey PRIMARY KEY (id)
);
ALTER SEQUENCE public.key_store_id_seq OWNED BY public.key_store.id;
ALTER TABLE ONLY public.key_store ALTER COLUMN id SET DEFAULT nextval('public.key_store_id_seq'::regclass);

CREATE TABLE public.message_types (
    id bigint DEFAULT nextval('message_types_id_seq'::regclass) NOT NULL,
    code character varying(4) NOT NULL,
    name character varying(100) NOT NULL,
    category character varying(50) NOT NULL,
    description character varying(255),
    processing_codes text,
    active boolean DEFAULT true,
    created_at timestamp without time zone DEFAULT now(),
    network character varying(20) DEFAULT 'DMAS'::character varying NOT NULL,
    direction character varying(12) DEFAULT 'ACQ_TO_ISS'::character varying NOT NULL,
    CONSTRAINT message_types_pkey PRIMARY KEY (id)
);
ALTER SEQUENCE public.message_types_id_seq OWNED BY public.message_types.id;
ALTER TABLE ONLY public.message_types ALTER COLUMN id SET DEFAULT nextval('public.message_types_id_seq'::regclass);

CREATE TABLE public.networks (
    id bigint DEFAULT nextval('networks_id_seq'::regclass) NOT NULL,
    code character varying(20) NOT NULL,
    name character varying(100) NOT NULL,
    description character varying(255),
    iso_version character varying(20),
    length_prefix_size integer,
    length_prefix_encoding character varying(10),
    header_type character varying(20),
    default_field_encoding character varying(10),
    mac_present boolean DEFAULT true,
    pin_block_format character varying(20),
    packager_class character varying(255),
    acquirer_host character varying(100),
    acquirer_rest_port integer,
    acquirer_jpos_port integer,
    issuer_host character varying(100),
    issuer_rest_port integer,
    issuer_iso_port integer,
    orchestrator_port integer,
    active boolean DEFAULT true NOT NULL,
    created_at timestamp without time zone DEFAULT now(),
    CONSTRAINT networks_pkey PRIMARY KEY (id),
    CONSTRAINT networks_code_key UNIQUE (code)
);
ALTER SEQUENCE public.networks_id_seq OWNED BY public.networks.id;
ALTER TABLE ONLY public.networks ALTER COLUMN id SET DEFAULT nextval('public.networks_id_seq'::regclass);

CREATE TABLE public.permissions (
    id bigint DEFAULT nextval('permissions_id_seq'::regclass) NOT NULL,
    code character varying(50) NOT NULL,
    label character varying(100) NOT NULL,
    category character varying(50) NOT NULL,
    created_at timestamp without time zone DEFAULT now(),
    CONSTRAINT permissions_pkey PRIMARY KEY (id),
    CONSTRAINT permissions_code_key UNIQUE (code)
);
ALTER SEQUENCE public.permissions_id_seq OWNED BY public.permissions.id;
ALTER TABLE ONLY public.permissions ALTER COLUMN id SET DEFAULT nextval('public.permissions_id_seq'::regclass);

CREATE TABLE public.results (
    id bigint DEFAULT nextval('results_id_seq'::regclass) NOT NULL,
    execution_id bigint NOT NULL,
    pan_masked character varying(20),
    de039 character varying(2),
    de038_auth_code character varying(6),
    approved boolean,
    duration_ms integer,
    request_hex text,
    response_hex text,
    executed_at timestamp without time zone DEFAULT now(),
    CONSTRAINT results_pkey PRIMARY KEY (id)
);
ALTER SEQUENCE public.results_id_seq OWNED BY public.results.id;
ALTER TABLE ONLY public.results ALTER COLUMN id SET DEFAULT nextval('public.results_id_seq'::regclass);

CREATE TABLE public.role_permissions (
    role_id bigint NOT NULL,
    permission_id bigint NOT NULL,
    CONSTRAINT role_permissions_pkey PRIMARY KEY (role_id, permission_id)
);

CREATE TABLE public.roles (
    id bigint DEFAULT nextval('roles_id_seq'::regclass) NOT NULL,
    code character varying(30) NOT NULL,
    label character varying(100) NOT NULL,
    description character varying(255),
    created_at timestamp without time zone DEFAULT now(),
    CONSTRAINT roles_pkey PRIMARY KEY (id),
    CONSTRAINT roles_code_key UNIQUE (code)
);
ALTER SEQUENCE public.roles_id_seq OWNED BY public.roles.id;
ALTER TABLE ONLY public.roles ALTER COLUMN id SET DEFAULT nextval('public.roles_id_seq'::regclass);

CREATE TABLE public.swam_acq_keys (
    id bigint DEFAULT nextval('swam_acq_keys_id_seq'::regclass) NOT NULL,
    member_group_id character varying(20) NOT NULL,
    key_type character varying(3) NOT NULL,
    key_length integer DEFAULT 24 NOT NULL,
    key_under_lmk character varying(64),
    key_under_kek character varying(64),
    kcv character varying(6),
    status character varying(10) DEFAULT 'ACTIVE'::character varying NOT NULL,
    created_at timestamp without time zone DEFAULT now(),
    CONSTRAINT swam_acq_keys_pkey PRIMARY KEY (id),
    CONSTRAINT uq_swam_acq_keys UNIQUE (member_group_id, key_type, status)
);
ALTER SEQUENCE public.swam_acq_keys_id_seq OWNED BY public.swam_acq_keys.id;
ALTER TABLE ONLY public.swam_acq_keys ALTER COLUMN id SET DEFAULT nextval('public.swam_acq_keys_id_seq'::regclass);

CREATE TABLE public.swam_acq_transactions (
    id bigint DEFAULT nextval('swam_acq_transactions_id_seq'::regclass) NOT NULL,
    pan character varying(19) NOT NULL,
    stan character varying(6) NOT NULL,
    transmission_dt character varying(10) NOT NULL,
    mti character varying(4) NOT NULL,
    processing_code character varying(6),
    amount bigint NOT NULL,
    currency character varying(3),
    response_code character varying(3),
    status character varying(10) DEFAULT 'SENT'::character varying NOT NULL,
    created_at timestamp without time zone DEFAULT now(),
    CONSTRAINT swam_acq_transactions_pkey PRIMARY KEY (id),
    CONSTRAINT uq_swam_acq_tx UNIQUE (stan, transmission_dt)
);
ALTER SEQUENCE public.swam_acq_transactions_id_seq OWNED BY public.swam_acq_transactions.id;
ALTER TABLE ONLY public.swam_acq_transactions ALTER COLUMN id SET DEFAULT nextval('public.swam_acq_transactions_id_seq'::regclass);

CREATE TABLE public.swam_cards (
    id bigint DEFAULT nextval('swam_cards_id_seq'::regclass) NOT NULL,
    pan character varying(19) NOT NULL,
    pin character varying(12) NOT NULL,
    balance bigint DEFAULT 0 NOT NULL,
    currency character varying(3) DEFAULT '504'::character varying NOT NULL,
    expiry character varying(4),
    status character varying(10) DEFAULT 'ACTIVE'::character varying NOT NULL,
    created_at timestamp without time zone DEFAULT now(),
    updated_at timestamp without time zone DEFAULT now(),
    CONSTRAINT swam_cards_pkey PRIMARY KEY (id),
    CONSTRAINT swam_cards_pan_key UNIQUE (pan)
);
ALTER SEQUENCE public.swam_cards_id_seq OWNED BY public.swam_cards.id;
ALTER TABLE ONLY public.swam_cards ALTER COLUMN id SET DEFAULT nextval('public.swam_cards_id_seq'::regclass);

CREATE TABLE public.swam_iss_keys (
    id bigint DEFAULT nextval('swam_iss_keys_id_seq'::regclass) NOT NULL,
    member_group_id character varying(20) NOT NULL,
    key_type character varying(3) NOT NULL,
    key_length integer DEFAULT 24 NOT NULL,
    key_under_lmk character varying(64),
    key_under_kek character varying(64),
    kcv character varying(6),
    status character varying(10) DEFAULT 'ACTIVE'::character varying NOT NULL,
    created_at timestamp without time zone DEFAULT now(),
    CONSTRAINT swam_iss_keys_pkey PRIMARY KEY (id),
    CONSTRAINT uq_swam_iss_keys UNIQUE (member_group_id, key_type, status)
);
ALTER SEQUENCE public.swam_iss_keys_id_seq OWNED BY public.swam_iss_keys.id;
ALTER TABLE ONLY public.swam_iss_keys ALTER COLUMN id SET DEFAULT nextval('public.swam_iss_keys_id_seq'::regclass);

CREATE TABLE public.swam_iss_transactions (
    id bigint DEFAULT nextval('swam_iss_transactions_id_seq'::regclass) NOT NULL,
    pan character varying(19) NOT NULL,
    stan character varying(6) NOT NULL,
    transmission_dt character varying(10) NOT NULL,
    mti character varying(4) NOT NULL,
    processing_code character varying(6),
    amount bigint NOT NULL,
    currency character varying(3),
    response_code character varying(3),
    status character varying(10) DEFAULT 'APPROVED'::character varying NOT NULL,
    created_at timestamp without time zone DEFAULT now(),
    reversed_at timestamp without time zone,
    CONSTRAINT swam_iss_transactions_pkey PRIMARY KEY (id),
    CONSTRAINT uq_swam_iss_tx UNIQUE (stan, transmission_dt)
);
ALTER SEQUENCE public.swam_iss_transactions_id_seq OWNED BY public.swam_iss_transactions.id;
ALTER TABLE ONLY public.swam_iss_transactions ALTER COLUMN id SET DEFAULT nextval('public.swam_iss_transactions_id_seq'::regclass);

CREATE TABLE public.swam_kek (
    id bigint DEFAULT nextval('swam_kek_id_seq'::regclass) NOT NULL,
    member_group_id character varying(20) NOT NULL,
    key_length integer DEFAULT 24 NOT NULL,
    kek_clear character varying(48),
    kek_under_acq_lmk character varying(128),
    kek_under_iss_lmk character varying(128),
    kcv character varying(6),
    status character varying(10) DEFAULT 'ACTIVE'::character varying NOT NULL,
    description character varying(255),
    created_at timestamp without time zone DEFAULT now(),
    CONSTRAINT swam_kek_pkey PRIMARY KEY (id),
    CONSTRAINT uq_swam_kek_group UNIQUE (member_group_id)
);
ALTER SEQUENCE public.swam_kek_id_seq OWNED BY public.swam_kek.id;
ALTER TABLE ONLY public.swam_kek ALTER COLUMN id SET DEFAULT nextval('public.swam_kek_id_seq'::regclass);

CREATE TABLE public.tests (
    id bigint DEFAULT nextval('tests_id_seq'::regclass) NOT NULL,
    name character varying(100) NOT NULL,
    description character varying(255),
    category character varying(50),
    message_type_id bigint,
    config text,
    expected_de039 character varying(2),
    active boolean DEFAULT true,
    created_at timestamp without time zone DEFAULT now(),
    created_by bigint,
    CONSTRAINT tests_pkey PRIMARY KEY (id)
);
ALTER SEQUENCE public.tests_id_seq OWNED BY public.tests.id;
ALTER TABLE ONLY public.tests ALTER COLUMN id SET DEFAULT nextval('public.tests_id_seq'::regclass);

CREATE TABLE public.tps_steps (
    id bigint DEFAULT nextval('tps_steps_id_seq'::regclass) NOT NULL,
    test_id bigint NOT NULL,
    step_order integer NOT NULL,
    start_seconds integer NOT NULL,
    end_seconds integer NOT NULL,
    tps_value integer NOT NULL,
    CONSTRAINT tps_steps_pkey PRIMARY KEY (id)
);
ALTER SEQUENCE public.tps_steps_id_seq OWNED BY public.tps_steps.id;
ALTER TABLE ONLY public.tps_steps ALTER COLUMN id SET DEFAULT nextval('public.tps_steps_id_seq'::regclass);

CREATE TABLE public.user_tests (
    user_id bigint NOT NULL,
    test_id bigint NOT NULL,
    assigned_at timestamp without time zone DEFAULT now(),
    assigned_by bigint,
    CONSTRAINT user_tests_pkey PRIMARY KEY (user_id, test_id)
);

CREATE TABLE public.users (
    id bigint DEFAULT nextval('users_id_seq'::regclass) NOT NULL,
    login character varying(50) NOT NULL,
    password character varying(255) NOT NULL,
    email character varying(100),
    role character varying(20) NOT NULL,
    active boolean DEFAULT true,
    created_at timestamp without time zone DEFAULT now(),
    created_by character varying(50),
    last_login timestamp without time zone,
    CONSTRAINT users_pkey PRIMARY KEY (id),
    CONSTRAINT users_login_key UNIQUE (login)
);
ALTER SEQUENCE public.users_id_seq OWNED BY public.users.id;
ALTER TABLE ONLY public.users ALTER COLUMN id SET DEFAULT nextval('public.users_id_seq'::regclass);

-- 5. Donnees de reference
-- networks (2 lignes)
INSERT INTO public.networks (id, code, name, description, iso_version, length_prefix_size, length_prefix_encoding, header_type, default_field_encoding, mac_present, pin_block_format, packager_class, acquirer_host, acquirer_rest_port, acquirer_jpos_port, issuer_host, issuer_rest_port, issuer_iso_port, orchestrator_port, active, created_at) VALUES ('1', 'DMAS', 'Mastercard DMAS', 'Reseau Mastercard DMAS (existant)', 'ISO8583:1987', '2', 'BINARY', 'NONE', 'EBCDIC', 't', 'ANSI_0', 'com.staging.sg.common.iso.McPackagerEbcdic', 'localhost', '8084', '8600', 'localhost', '8501', '8500', '8080', 't', '2026-07-06 10:15:29.758749');
INSERT INTO public.networks (id, code, name, description, iso_version, length_prefix_size, length_prefix_encoding, header_type, default_field_encoding, mac_present, pin_block_format, packager_class, acquirer_host, acquirer_rest_port, acquirer_jpos_port, issuer_host, issuer_rest_port, issuer_iso_port, orchestrator_port, active, created_at) VALUES ('2', 'SWAM', 'Switch Al Maghrib', 'Switch national marocain (HPS HSID/PowerCARD)', 'ISO8583:1993', '4', 'ASCII', 'POWERCARD', 'ASCII', 't', 'ANSI_0', 'com.staging.sg.common.iso.SwamPackager', 'localhost', '8094', NULL, 'localhost', '8511', '8510', '8080', 't', '2026-07-06 10:15:29.758749');

-- roles (3 lignes)
INSERT INTO public.roles (id, code, label, description, created_at) VALUES ('1', 'ADMIN', 'Administrateur', 'Acces complet a toutes les fonctions', '2026-06-22 15:20:56.688739');
INSERT INTO public.roles (id, code, label, description, created_at) VALUES ('3', 'OBSERVATEUR', 'Observateur', 'Lecture seule : consultation et export', '2026-06-22 15:20:56.688739');
INSERT INTO public.roles (id, code, label, description, created_at) VALUES ('4', 'EXPLOITATION', 'Exploitation', 'Equivalent testeur', '2026-06-22 15:20:56.688739');

-- permissions (12 lignes)
INSERT INTO public.permissions (id, code, label, category, created_at) VALUES ('1', 'USER_MANAGE', 'Gerer les utilisateurs', 'GESTION', '2026-06-22 15:20:56.887416');
INSERT INTO public.permissions (id, code, label, category, created_at) VALUES ('2', 'ROLE_MANAGE', 'Gerer roles et permissions', 'GESTION', '2026-06-22 15:20:56.887416');
INSERT INTO public.permissions (id, code, label, category, created_at) VALUES ('3', 'CATALOG_MANAGE', 'Gerer les catalogues', 'GESTION', '2026-06-22 15:20:56.887416');
INSERT INTO public.permissions (id, code, label, category, created_at) VALUES ('4', 'CAMPAIGN_VIEW', 'Consulter les campagnes', 'CAMPAGNE', '2026-06-22 15:20:56.887416');
INSERT INTO public.permissions (id, code, label, category, created_at) VALUES ('5', 'CAMPAIGN_CREATE', 'Creer/editer une campagne', 'CAMPAGNE', '2026-06-22 15:20:56.887416');
INSERT INTO public.permissions (id, code, label, category, created_at) VALUES ('6', 'CAMPAIGN_GENERATE', 'Generer les transactions', 'CAMPAGNE', '2026-06-22 15:20:56.887416');
INSERT INTO public.permissions (id, code, label, category, created_at) VALUES ('7', 'CAMPAIGN_EXPORT', 'Exporter (JSON/CSV)', 'CAMPAGNE', '2026-06-22 15:20:56.887416');
INSERT INTO public.permissions (id, code, label, category, created_at) VALUES ('8', 'CARD_PROVISION', 'Provisionner les cartes', 'ORCHESTRATION', '2026-06-22 15:20:56.887416');
INSERT INTO public.permissions (id, code, label, category, created_at) VALUES ('9', 'CAMPAIGN_REPLAY', 'Rejouer une campagne', 'ORCHESTRATION', '2026-06-22 15:20:56.887416');
INSERT INTO public.permissions (id, code, label, category, created_at) VALUES ('10', 'TPS_CREATE', 'Creer/editer un test de charge', 'CHARGE', '2026-06-22 15:20:56.887416');
INSERT INTO public.permissions (id, code, label, category, created_at) VALUES ('11', 'TPS_RUN', 'Lancer/arreter une execution', 'CHARGE', '2026-06-22 15:20:56.887416');
INSERT INTO public.permissions (id, code, label, category, created_at) VALUES ('12', 'EXECUTION_VIEW', 'Consulter executions/rapports', 'CHARGE', '2026-06-22 15:20:56.887416');

-- role_permissions (23 lignes)
INSERT INTO public.role_permissions (role_id, permission_id) VALUES ('1', '1');
INSERT INTO public.role_permissions (role_id, permission_id) VALUES ('1', '2');
INSERT INTO public.role_permissions (role_id, permission_id) VALUES ('1', '3');
INSERT INTO public.role_permissions (role_id, permission_id) VALUES ('1', '4');
INSERT INTO public.role_permissions (role_id, permission_id) VALUES ('1', '5');
INSERT INTO public.role_permissions (role_id, permission_id) VALUES ('1', '6');
INSERT INTO public.role_permissions (role_id, permission_id) VALUES ('1', '7');
INSERT INTO public.role_permissions (role_id, permission_id) VALUES ('1', '8');
INSERT INTO public.role_permissions (role_id, permission_id) VALUES ('1', '9');
INSERT INTO public.role_permissions (role_id, permission_id) VALUES ('1', '10');
INSERT INTO public.role_permissions (role_id, permission_id) VALUES ('1', '11');
INSERT INTO public.role_permissions (role_id, permission_id) VALUES ('1', '12');
INSERT INTO public.role_permissions (role_id, permission_id) VALUES ('4', '4');
INSERT INTO public.role_permissions (role_id, permission_id) VALUES ('4', '5');
INSERT INTO public.role_permissions (role_id, permission_id) VALUES ('4', '6');
INSERT INTO public.role_permissions (role_id, permission_id) VALUES ('4', '7');
INSERT INTO public.role_permissions (role_id, permission_id) VALUES ('4', '9');
INSERT INTO public.role_permissions (role_id, permission_id) VALUES ('4', '10');
INSERT INTO public.role_permissions (role_id, permission_id) VALUES ('4', '11');
INSERT INTO public.role_permissions (role_id, permission_id) VALUES ('4', '12');
INSERT INTO public.role_permissions (role_id, permission_id) VALUES ('3', '4');
INSERT INTO public.role_permissions (role_id, permission_id) VALUES ('3', '7');
INSERT INTO public.role_permissions (role_id, permission_id) VALUES ('3', '12');

-- users (3 lignes)
INSERT INTO public.users (id, login, password, email, role, active, created_at, created_by, last_login) VALUES ('4', 'obs1', '$2a$10$WWj2izEIA8Ej19eNHLR6..eQUifw3cwTDsXUCCzhqr7CeZo4ozuoi', 'obs@test.fr', 'OBSERVATEUR', 't', '2026-06-22 16:41:47.427876', 'admin', '2026-06-29 11:14:39.125978');
INSERT INTO public.users (id, login, password, email, role, active, created_at, created_by, last_login) VALUES ('2', 'admin', '$2a$10$tpc6X1hk/U2wELdRSH4qQ.L53SlDmHog/Bhfh2Kn6eM7zOVZst/ey', 'admin@staging.com', 'ADMIN', 't', '2026-06-14 11:26:58.729215', 'system', '2026-07-06 11:20:43.913684');
INSERT INTO public.users (id, login, password, email, role, active, created_at, created_by, last_login) VALUES ('3', 'mohamed', '$2a$10$Jei.bDl1Y40XEPSQgdIegOuEOXVJRvGJnldTPP5Pg8i18wbARi9hC', 'mohamed@staging.com', 'EXPLOITATION', 't', '2026-06-14 15:35:21.662724', 'admin', '2026-06-28 18:03:10.005053');

-- bin_range (5 lignes)
INSERT INTO public.bin_range (id, code, product_name, network, pan_length, is_range, enabled) VALUES ('1', '51-55', 'Mastercard (plage classique 51-55)', 'MASTERCARD', '16', 't', 't');
INSERT INTO public.bin_range (id, code, product_name, network, pan_length, is_range, enabled) VALUES ('2', '2221-2720', 'Mastercard (nouvelle plage 2-series)', 'MASTERCARD', '16', 't', 't');
INSERT INTO public.bin_range (id, code, product_name, network, pan_length, is_range, enabled) VALUES ('3', '513330', 'Mastercard Standard (BIN test)', 'MASTERCARD', '16', 'f', 't');
INSERT INTO public.bin_range (id, code, product_name, network, pan_length, is_range, enabled) VALUES ('4', '541333', 'Mastercard Gold (BIN test)', 'MASTERCARD', '16', 'f', 't');
INSERT INTO public.bin_range (id, code, product_name, network, pan_length, is_range, enabled) VALUES ('5', '555555', 'Mastercard World (BIN test)', 'MASTERCARD', '16', 'f', 't');

-- iso_field_catalog (17 lignes)
INSERT INTO public.iso_field_catalog (id, field_code, name, description, gen_strategy, enabled, display_order) VALUES ('1', 'DE2', 'PAN', 'Primary Account Number', 'PAN_LUHN', 't', '10');
INSERT INTO public.iso_field_catalog (id, field_code, name, description, gen_strategy, enabled, display_order) VALUES ('2', 'DE3', 'Processing Code', 'Type de transaction', 'PROCESSING_CODE', 't', '20');
INSERT INTO public.iso_field_catalog (id, field_code, name, description, gen_strategy, enabled, display_order) VALUES ('3', 'DE4', 'Transaction Amount', 'Montant en centimes', 'AMOUNT_RANGE', 't', '30');
INSERT INTO public.iso_field_catalog (id, field_code, name, description, gen_strategy, enabled, display_order) VALUES ('4', 'DE7', 'Transmission DateTime', 'MMDDhhmmss UTC', 'DATE_NOW', 't', '40');
INSERT INTO public.iso_field_catalog (id, field_code, name, description, gen_strategy, enabled, display_order) VALUES ('5', 'DE11', 'STAN', 'System Trace Audit Number', 'STAN', 't', '50');
INSERT INTO public.iso_field_catalog (id, field_code, name, description, gen_strategy, enabled, display_order) VALUES ('6', 'DE12', 'Local Transaction Time', 'HHmmss', 'LOCAL_TIME', 't', '60');
INSERT INTO public.iso_field_catalog (id, field_code, name, description, gen_strategy, enabled, display_order) VALUES ('7', 'DE13', 'Local Transaction Date', 'MMDD', 'LOCAL_DATE', 't', '70');
INSERT INTO public.iso_field_catalog (id, field_code, name, description, gen_strategy, enabled, display_order) VALUES ('8', 'DE14', 'Expiration Date', 'YYMM (futur)', 'EXPIRY', 't', '80');
INSERT INTO public.iso_field_catalog (id, field_code, name, description, gen_strategy, enabled, display_order) VALUES ('9', 'DE18', 'MCC', 'Merchant Category Code', 'MCC', 't', '90');
INSERT INTO public.iso_field_catalog (id, field_code, name, description, gen_strategy, enabled, display_order) VALUES ('10', 'DE22', 'POS Entry Mode', 'Mode de saisie', 'POS_ENTRY', 't', '100');
INSERT INTO public.iso_field_catalog (id, field_code, name, description, gen_strategy, enabled, display_order) VALUES ('11', 'DE25', 'POS Condition Code', 'Condition POS', 'POS_CONDITION', 't', '110');
INSERT INTO public.iso_field_catalog (id, field_code, name, description, gen_strategy, enabled, display_order) VALUES ('12', 'DE32', 'Acquirer ID', 'Acquiring Institution ID', 'ACQUIRER_ID', 't', '120');
INSERT INTO public.iso_field_catalog (id, field_code, name, description, gen_strategy, enabled, display_order) VALUES ('13', 'DE37', 'RRN', 'Retrieval Reference Number', 'RRN', 't', '130');
INSERT INTO public.iso_field_catalog (id, field_code, name, description, gen_strategy, enabled, display_order) VALUES ('14', 'DE41', 'Terminal ID', 'Acceptor Terminal ID', 'TERMINAL_ID', 't', '140');
INSERT INTO public.iso_field_catalog (id, field_code, name, description, gen_strategy, enabled, display_order) VALUES ('15', 'DE42', 'Merchant ID', 'Acceptor ID Code', 'MERCHANT_ID', 't', '150');
INSERT INTO public.iso_field_catalog (id, field_code, name, description, gen_strategy, enabled, display_order) VALUES ('16', 'DE43', 'Merchant Name/Location', 'Nom + ville + pays (ans-40)', 'MERCHANT_NAME', 't', '160');
INSERT INTO public.iso_field_catalog (id, field_code, name, description, gen_strategy, enabled, display_order) VALUES ('17', 'DE49', 'Currency Code', 'Devise (n-3)', 'CURRENCY', 't', '170');

-- message_types (9 lignes)
INSERT INTO public.message_types (id, code, name, category, description, processing_codes, active, created_at, network, direction) VALUES ('1', '0100', 'Authorization Request', 'AUTHORIZATION', 'Mastercard authorization request', '[{"code":"000000","label":"Purchase"},{"code":"010000","label":"Cash Advance"},{"code":"200000","label":"Refund"},{"code":"310000","label":"Balance Inquiry"},{"code":"340000","label":"Mini Statement"}]', 't', '2026-06-13 22:49:15.430144', 'DMAS', 'ACQ_TO_ISS');
INSERT INTO public.message_types (id, code, name, category, description, processing_codes, active, created_at, network, direction) VALUES ('2', '0200', 'Financial Transaction Request', 'FINANCIAL', 'Financial transaction request', '[{"code":"010000","label":"Cash Withdrawal"},{"code":"000000","label":"Purchase"}]', 't', '2026-06-13 22:49:15.430144', 'DMAS', 'ACQ_TO_ISS');
INSERT INTO public.message_types (id, code, name, category, description, processing_codes, active, created_at, network, direction) VALUES ('3', '0400', 'Reversal Request', 'REVERSAL', 'Transaction reversal request', '[{"code":"000000","label":"Purchase Reversal"},{"code":"010000","label":"Cash Reversal"}]', 't', '2026-06-13 22:49:15.430144', 'DMAS', 'ACQ_TO_ISS');
INSERT INTO public.message_types (id, code, name, category, description, processing_codes, active, created_at, network, direction) VALUES ('14', '1100', 'Demande d''autorisation', 'AUTHORIZATION', 'Autorisation SWAM (PowerCARD HSID)', '[{"code":"000000","label":"Achat de biens & services"},{"code":"010000","label":"Cash advance"},{"code":"170000","label":"Cash"},{"code":"310000","label":"Demande de solde"},{"code":"960000","label":"Achat sur GAB"}]', 't', '2026-07-06 09:59:22.698105', 'SWAM', 'ACQ_TO_ISS');
INSERT INTO public.message_types (id, code, name, category, description, processing_codes, active, created_at, network, direction) VALUES ('15', '1200', 'Demande de transaction financiere', 'FINANCIAL', 'Transaction financiere SWAM (PowerCARD HSID)', '[{"code":"000000","label":"Achat de biens & services"},{"code":"010000","label":"Cash advance"},{"code":"310000","label":"Demande de solde"}]', 't', '2026-07-06 09:59:22.698105', 'SWAM', 'ACQ_TO_ISS');
INSERT INTO public.message_types (id, code, name, category, description, processing_codes, active, created_at, network, direction) VALUES ('16', '1420', 'Avis d''annulation acquereur', 'REVERSAL', 'Reversal / annulation acquereur SWAM', '[{"code":"000000","label":"Annulation achat"},{"code":"010000","label":"Annulation cash advance"}]', 't', '2026-07-06 09:59:22.698105', 'SWAM', 'ACQ_TO_ISS');
INSERT INTO public.message_types (id, code, name, category, description, processing_codes, active, created_at, network, direction) VALUES ('4', '0800', 'Network Management Request', 'NETWORK', 'Network management message', '[{"code":"301","label":"Sign-on"},{"code":"302","label":"Echo Test"}]', 't', '2026-06-13 22:49:15.430144', 'DMAS', 'BOTH');
INSERT INTO public.message_types (id, code, name, category, description, processing_codes, active, created_at, network, direction) VALUES ('5', '0820', 'Key Exchange Request', 'NETWORK', 'Key exchange message', '[{"code":"101","label":"ZMK Exchange"},{"code":"102","label":"ZPK Exchange"},{"code":"103","label":"ZAK Exchange"}]', 't', '2026-06-13 22:49:15.430144', 'DMAS', 'BOTH');
INSERT INTO public.message_types (id, code, name, category, description, processing_codes, active, created_at, network, direction) VALUES ('17', '1804', 'Demande de gestion de reseau', 'NETWORK', 'Gestion reseau SWAM (DE24 : 801 sign-on, 803 echo, 802 sign-off)', '[{"code":"801","label":"Ouverture de session"},{"code":"803","label":"Echo test"},{"code":"802","label":"Fermeture de session"}]', 't', '2026-07-06 09:59:22.698105', 'SWAM', 'BOTH');

-- campaigns (7 lignes)
INSERT INTO public.campaigns (id, name, description, category, config, expected_de039, active, created_at, created_by, sla_p95_max_ms, sla_error_rate_max, sla_approval_min, stop_on_error_rate, network, initiator) VALUES ('1', 'CAMP-MULTIPALIERS', 'montee 5->15->5 TPS, SLA p95<100ms', 'AUTHORIZATION', '{"DE002_PAN":"5321962145453348","DE004_AMOUNT":0}', NULL, 't', '2026-06-26 11:58:49.89555', '2', '600', '5.00', '95.00', NULL, 'DMAS', 'ACQUIRER');
INSERT INTO public.campaigns (id, name, description, category, config, expected_de039, active, created_at, created_by, sla_p95_max_ms, sla_error_rate_max, sla_approval_min, stop_on_error_rate, network, initiator) VALUES ('2', 'CAMP-RANDOM-PIN', 'tirage cartes reelles + PIN', 'AUTHORIZATION', '{"DE002_PAN_MODE":"RANDOM","WITH_PIN":true,"DE004_AMOUNT":1000}', NULL, 't', '2026-06-27 20:15:32.666486', '2', '600', '10.00', '80.00', NULL, 'DMAS', 'ACQUIRER');
INSERT INTO public.campaigns (id, name, description, category, config, expected_de039, active, created_at, created_by, sla_p95_max_ms, sla_error_rate_max, sla_approval_min, stop_on_error_rate, network, initiator) VALUES ('11', 'CAMP-E2E', NULL, 'AUTHORIZATION', '{"DE002_PAN_MODE":"RANDOM","WITH_PIN":false,"VARIABLE_FIELDS":{"AMOUNT":{"mode":"RANGE","min":1000,"max":50000}}}', NULL, 't', '2026-06-30 15:44:47.489963', '2', NULL, '10.00', NULL, '20.00', 'DMAS', 'ACQUIRER');
INSERT INTO public.campaigns (id, name, description, category, config, expected_de039, active, created_at, created_by, sla_p95_max_ms, sla_error_rate_max, sla_approval_min, stop_on_error_rate, network, initiator) VALUES ('12', 'CAMP-E2E', NULL, 'AUTHORIZATION', '{"DE002_PAN_MODE":"RANDOM","WITH_PIN":false,"VARIABLE_FIELDS":{"AMOUNT":{"mode":"RANGE","min":1000,"max":50000}}}', NULL, 't', '2026-06-30 15:47:19.347708', '2', NULL, '10.00', NULL, '20.00', 'DMAS', 'ACQUIRER');
INSERT INTO public.campaigns (id, name, description, category, config, expected_de039, active, created_at, created_by, sla_p95_max_ms, sla_error_rate_max, sla_approval_min, stop_on_error_rate, network, initiator) VALUES ('13', 'CAMP-E2E', NULL, 'AUTHORIZATION', '{"DE002_PAN_MODE":"RANDOM","WITH_PIN":false,"VARIABLE_FIELDS":{"AMOUNT":{"mode":"RANGE","min":1000,"max":50000}}}', NULL, 't', '2026-06-30 15:48:53.807315', '2', NULL, '10.00', NULL, '20.00', 'DMAS', 'ACQUIRER');
INSERT INTO public.campaigns (id, name, description, category, config, expected_de039, active, created_at, created_by, sla_p95_max_ms, sla_error_rate_max, sla_approval_min, stop_on_error_rate, network, initiator) VALUES ('14', 'CAMP-E2E', NULL, 'DMAS', '{"DE002_PAN_MODE":"RANDOM","WITH_PIN":false,"VARIABLE_FIELDS":{"AMOUNT":{"mode":"RANGE","min":1000,"max":50000}}}', NULL, 't', '2026-07-06 10:56:03.827311', '2', NULL, '10.00', NULL, '20.00', 'DMAS', 'ACQUIRER');
INSERT INTO public.campaigns (id, name, description, category, config, expected_de039, active, created_at, created_by, sla_p95_max_ms, sla_error_rate_max, sla_approval_min, stop_on_error_rate, network, initiator) VALUES ('15', 'CAMP-E2E', NULL, 'AUTHORIZATION', '{"DE002_PAN_MODE":"RANDOM","WITH_PIN":false,"VARIABLE_FIELDS":{"AMOUNT":{"mode":"RANGE","min":1000,"max":50000}}}', NULL, 't', '2026-07-06 11:05:13.946284', '2', NULL, '10.00', NULL, '20.00', 'DMAS', 'ACQUIRER');

-- tests (12 lignes)
INSERT INTO public.tests (id, name, description, category, message_type_id, config, expected_de039, active, created_at, created_by) VALUES ('4', 'Achat nominal CB', 'Test achat standard puce EMV', 'AUTHORIZATION', '1', '{"DE003_PROCESSING_CODE":"000000","DE004_AMOUNT":5000,"DE018_MCC":"5411","DE022_POS_ENTRY_MODE":"051","DE049_CURRENCY_CODE":"978","DE052_PIN":"1234"}', '00', 't', '2026-06-14 15:35:22.514351', '2');
INSERT INTO public.tests (id, name, description, category, message_type_id, config, expected_de039, active, created_at, created_by) VALUES ('5', 'Achat nominal CB', 'Test achat standard puce EMV', 'AUTHORIZATION', '1', '{"DE003_PROCESSING_CODE":"000000","DE004_AMOUNT":5000,"DE018_MCC":"5411","DE022_POS_ENTRY_MODE":"051","DE049_CURRENCY_CODE":"978","DE052_PIN":"1234"}', '00', 't', '2026-06-14 15:36:45.218162', '2');
INSERT INTO public.tests (id, name, description, category, message_type_id, config, expected_de039, active, created_at, created_by) VALUES ('6', '100_Achat_Par_Seconde', 'Test de charge', 'AUTHORIZATION', '1', '{"DE004_AMOUNT":5000,"DE052_PIN":"1234"}', '00', 't', '2026-06-14 15:55:33.401485', '2');
INSERT INTO public.tests (id, name, description, category, message_type_id, config, expected_de039, active, created_at, created_by) VALUES ('7', '100_Achat_Par_Seconde', 'Test de charge', 'AUTHORIZATION', '1', '{"DE004_AMOUNT":5000,"DE052_PIN":"1234"}', '00', 'f', '2026-06-14 15:55:41.260566', '2');
INSERT INTO public.tests (id, name, description, category, message_type_id, config, expected_de039, active, created_at, created_by) VALUES ('8', '5_TPS_10_Secondes', 'Test de charge 5 TPS pendant 10 secondes', 'AUTHORIZATION', '1', '{"DE003_PROCESSING_CODE":"000000","DE004_AMOUNT":5000,"DE018_MCC":"5411","DE052_PIN":"1234"}', '00', 't', '2026-06-16 15:52:29.131596', '2');
INSERT INTO public.tests (id, name, description, category, message_type_id, config, expected_de039, active, created_at, created_by) VALUES ('9', '5_TPS_10_Secondes', 'Test de charge 5 TPS pendant 10 secondes', 'AUTHORIZATION', '1', '{"DE003_PROCESSING_CODE":"000000","DE004_AMOUNT":5000,"DE018_MCC":"5411","DE052_PIN":"1234"}', '00', 't', '2026-06-17 12:03:08.760008', '2');
INSERT INTO public.tests (id, name, description, category, message_type_id, config, expected_de039, active, created_at, created_by) VALUES ('10', 'Charge DMAS Purchase', 'test charge DMAS', 'PERF', '1', '{"DE002_PAN":"5133309842723011","DE004_AMOUNT":5000,"DE003_PROCESSING_CODE":"000000","DE018_MCC":"5411","DE049_CURRENCY_CODE":"840"}', '00', 't', '2026-06-22 11:47:17.903212', '2');
INSERT INTO public.tests (id, name, description, category, message_type_id, config, expected_de039, active, created_at, created_by) VALUES ('11', 'Montee en charge DMAS', '3 paliers 10-50-100 TPS', 'PERF', '1', '{"DE002_PAN":"5133309842723011","DE004_AMOUNT":5000,"DE003_PROCESSING_CODE":"000000","DE018_MCC":"5411","DE049_CURRENCY_CODE":"840"}', '00', 't', '2026-06-22 14:29:17.897617', '2');
INSERT INTO public.tests (id, name, description, category, message_type_id, config, expected_de039, active, created_at, created_by) VALUES ('12', 'TEST-SIMPLE-JPOS', 'Autorisation simple via connexion permanente', 'DMAS', NULL, '{"DE002_PAN":"5321962145453348","DE004_AMOUNT":49,"DE003_PROCESSING_CODE":"000000","DE018_MCC":"5999","DE022_POS_ENTRY_MODE":"051","DE049_CURRENCY_CODE":"840"}', NULL, 't', '2026-06-25 10:29:48.375154', '2');
INSERT INTO public.tests (id, name, description, category, message_type_id, config, expected_de039, active, created_at, created_by) VALUES ('13', 'TEST-TPS-JPOS', 'Charge 5 TPS via connexion permanente', 'DMAS', NULL, '{"DE002_PAN":"5321962145453348","DE004_AMOUNT":49,"DE003_PROCESSING_CODE":"000000","DE018_MCC":"5999","DE022_POS_ENTRY_MODE":"051","DE049_CURRENCY_CODE":"840"}', NULL, 't', '2026-06-25 10:34:52.727325', '2');
INSERT INTO public.tests (id, name, description, category, message_type_id, config, expected_de039, active, created_at, created_by) VALUES ('14', 'TEST-TPS-LOADTEST', 'charge 10 TPS 5s', 'DMAS', NULL, '{"DE002_PAN":"5321962145453348","DE004_AMOUNT":0,"DE003_PROCESSING_CODE":"000000"}', NULL, 't', '2026-06-25 16:27:21.418562', '2');
INSERT INTO public.tests (id, name, description, category, message_type_id, config, expected_de039, active, created_at, created_by) VALUES ('15', 'POST-CLEANUP-TPS', NULL, 'DMAS', NULL, '{"DE002_PAN":"5321962145453348","DE004_AMOUNT":0}', NULL, 't', '2026-06-26 09:40:39.706488', '2');

-- dmas_cards (7 lignes)
INSERT INTO public.dmas_cards (id, pan, pin, balance, currency, expiry, status, created_at, updated_at) VALUES ('1', '5413330089010444', '1234', '98721117', '840', '2812', 'ACTIVE', '2026-06-21 14:03:30.775137', '2026-06-28 18:41:54.199313');
INSERT INTO public.dmas_cards (id, pan, pin, balance, currency, expiry, status, created_at, updated_at) VALUES ('4', '5133300209227621', '1234', '99177630', '840', '2806', 'ACTIVE', '2026-06-22 10:19:46.110313', '2026-06-28 12:48:54.941395');
INSERT INTO public.dmas_cards (id, pan, pin, balance, currency, expiry, status, created_at, updated_at) VALUES ('5', '5133305622642819', '1234', '98776905', '840', '2706', 'ACTIVE', '2026-06-22 10:19:46.137241', '2026-06-28 12:48:54.941395');
INSERT INTO public.dmas_cards (id, pan, pin, balance, currency, expiry, status, created_at, updated_at) VALUES ('7', '5321962145453348', '1234', '99163418', '840', NULL, 'ACTIVE', '2026-06-24 15:55:34.916757', '2026-06-28 12:48:54.941395');
INSERT INTO public.dmas_cards (id, pan, pin, balance, currency, expiry, status, created_at, updated_at) VALUES ('6', '5133306099184459', '1234', '99210907', '840', '2906', 'ACTIVE', '2026-06-22 10:19:46.478328', '2026-06-28 12:48:54.941395');
INSERT INTO public.dmas_cards (id, pan, pin, balance, currency, expiry, status, created_at, updated_at) VALUES ('2', '5133309842723011', '1234', '99235222', '840', '2706', 'ACTIVE', '2026-06-22 10:19:45.957721', '2026-06-28 12:48:54.941395');
INSERT INTO public.dmas_cards (id, pan, pin, balance, currency, expiry, status, created_at, updated_at) VALUES ('3', '5133300371816755', '1234', '99154372', '840', '2706', 'ACTIVE', '2026-06-22 10:19:46.080394', '2026-06-28 12:48:54.941395');

-- dmas_kek (1 lignes)
INSERT INTO public.dmas_kek (id, member_group_id, key_length, kek_clear, kek_under_acq_lmk, kek_under_iss_lmk, kcv, status, description, created_at) VALUES ('1', 'TESTGRP01', '24', '0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF', '27EA47D7E8F38B6327EA47D7E8F38B6327EA47D7E8F38B63', '96569D722B396F0396569D722B396F0396569D722B396F03', 'D5D44F', 'ACTIVE', 'KEK bootstrap (acquirer side)', '2026-06-19 16:06:49.112154');

-- dmas_acq_keys (2 lignes)
INSERT INTO public.dmas_acq_keys (id, member_group_id, key_type, key_length, key_under_lmk, key_under_kek, kcv, status, created_at) VALUES ('2', 'TESTGRP01', 'MAK', '24', '00BE0E3A50027F612BB0E4FD815B8FC000BE0E3A50027F61', '67DC8B70682295244457EA734EC1D28E67DC8B7068229524', '422576', 'ACTIVE', '2026-06-19 20:25:11.997172');
INSERT INTO public.dmas_acq_keys (id, member_group_id, key_type, key_length, key_under_lmk, key_under_kek, kcv, status, created_at) VALUES ('1', 'TESTGRP01', 'PEK', '24', '80353B4F112E27A4AA4F9E882C3C304080353B4F112E27A4', '49B093C17F9B50A7063324AB5FAC15FD49B093C17F9B50A7', '20C28F', 'ACTIVE', '2026-06-19 20:25:10.445916');

-- dmas_iss_keys (2 lignes)
INSERT INTO public.dmas_iss_keys (id, member_group_id, key_type, key_length, key_under_lmk, key_under_kek, kcv, status, created_at) VALUES ('2', 'TESTGRP01', 'MAK', '24', '1AC902463AD657D4B289A6726EA8BAA01AC902463AD657D4', '67DC8B70682295244457EA734EC1D28E67DC8B7068229524', '422576', 'ACTIVE', '2026-06-19 20:25:11.989191');
INSERT INTO public.dmas_iss_keys (id, member_group_id, key_type, key_length, key_under_lmk, key_under_kek, kcv, status, created_at) VALUES ('1', 'TESTGRP01', 'PEK', '24', '5FDB4FDA6F63F564E7A6277B0F3A854B5FDB4FDA6F63F564', '49B093C17F9B50A7063324AB5FAC15FD49B093C17F9B50A7', '20C28F', 'ACTIVE', '2026-06-19 20:25:10.349149');

-- swam_cards (3 lignes)
INSERT INTO public.swam_cards (id, pan, pin, balance, currency, expiry, status, created_at, updated_at) VALUES ('2', '5321000000000011', '1234', '500', '504', '2812', 'ACTIVE', '2026-07-06 15:59:10.436823', '2026-07-06 15:59:10.436823');
INSERT INTO public.swam_cards (id, pan, pin, balance, currency, expiry, status, created_at, updated_at) VALUES ('3', '5321000000000029', '1234', '100000', '504', '2812', 'BLOCKED', '2026-07-06 15:59:10.438685', '2026-07-06 15:59:10.438685');
INSERT INTO public.swam_cards (id, pan, pin, balance, currency, expiry, status, created_at, updated_at) VALUES ('1', '5321962145453348', '1234', '70000', '504', '2812', 'ACTIVE', '2026-07-06 15:59:10.365613', '2026-07-08 09:00:22.339737');

-- swam_kek (1 lignes)
INSERT INTO public.swam_kek (id, member_group_id, key_length, kek_clear, kek_under_acq_lmk, kek_under_iss_lmk, kcv, status, description, created_at) VALUES ('1', 'TESTGRP01', '24', '0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF', '004F3D7ED75197FC004F3D7ED75197FC004F3D7ED75197FC', 'E98805FBB4DE36AC2C7742E242CF80D8', 'D5D44F', 'ACTIVE', 'KEK bootstrap SWAM (issuer side)', '2026-07-07 09:59:37.23851');

-- 6. Reset sequences apres inserts
SELECT setval('public.networks_id_seq', COALESCE((SELECT MAX(id) FROM public.networks),1));
SELECT setval('public.roles_id_seq', COALESCE((SELECT MAX(id) FROM public.roles),1));
SELECT setval('public.permissions_id_seq', COALESCE((SELECT MAX(id) FROM public.permissions),1));
SELECT setval('public.users_id_seq', COALESCE((SELECT MAX(id) FROM public.users),1));
SELECT setval('public.bin_range_id_seq', COALESCE((SELECT MAX(id) FROM public.bin_range),1));
SELECT setval('public.iso_field_catalog_id_seq', COALESCE((SELECT MAX(id) FROM public.iso_field_catalog),1));
SELECT setval('public.message_types_id_seq', COALESCE((SELECT MAX(id) FROM public.message_types),1));
SELECT setval('public.campaigns_id_seq', COALESCE((SELECT MAX(id) FROM public.campaigns),1));
SELECT setval('public.tests_id_seq', COALESCE((SELECT MAX(id) FROM public.tests),1));
SELECT setval('public.dmas_cards_id_seq', COALESCE((SELECT MAX(id) FROM public.dmas_cards),1));
SELECT setval('public.dmas_kek_id_seq', COALESCE((SELECT MAX(id) FROM public.dmas_kek),1));
SELECT setval('public.dmas_acq_keys_id_seq', COALESCE((SELECT MAX(id) FROM public.dmas_acq_keys),1));
SELECT setval('public.dmas_iss_keys_id_seq', COALESCE((SELECT MAX(id) FROM public.dmas_iss_keys),1));
SELECT setval('public.swam_cards_id_seq', COALESCE((SELECT MAX(id) FROM public.swam_cards),1));
SELECT setval('public.swam_kek_id_seq', COALESCE((SELECT MAX(id) FROM public.swam_kek),1));

-- 7. Contraintes FK
ALTER TABLE ONLY public.acq_advices ADD CONSTRAINT acq_advices_acq_auth_id_fkey FOREIGN KEY (acq_auth_id) REFERENCES public.acq_authorizations(id);
ALTER TABLE ONLY public.acq_advices ADD CONSTRAINT acq_advices_execution_id_fkey FOREIGN KEY (execution_id) REFERENCES public.executions(id);
ALTER TABLE ONLY public.acq_authorizations ADD CONSTRAINT acq_authorizations_execution_id_fkey FOREIGN KEY (execution_id) REFERENCES public.executions(id);
ALTER TABLE ONLY public.acq_ipm_files ADD CONSTRAINT acq_ipm_files_execution_id_fkey FOREIGN KEY (execution_id) REFERENCES public.executions(id);
ALTER TABLE ONLY public.acq_ipm_records ADD CONSTRAINT acq_ipm_records_ipm_file_id_fkey FOREIGN KEY (ipm_file_id) REFERENCES public.acq_ipm_files(id) ON DELETE CASCADE;
ALTER TABLE ONLY public.acq_reversals ADD CONSTRAINT acq_reversals_acq_auth_id_fkey FOREIGN KEY (acq_auth_id) REFERENCES public.acq_authorizations(id);
ALTER TABLE ONLY public.acq_reversals ADD CONSTRAINT acq_reversals_execution_id_fkey FOREIGN KEY (execution_id) REFERENCES public.executions(id);
ALTER TABLE ONLY public.campaign_execution_results ADD CONSTRAINT campaign_execution_results_execution_id_fkey FOREIGN KEY (execution_id) REFERENCES public.campaign_executions(id) ON DELETE CASCADE;
ALTER TABLE ONLY public.campaign_executions ADD CONSTRAINT campaign_executions_campaign_id_fkey FOREIGN KEY (campaign_id) REFERENCES public.campaigns(id);
ALTER TABLE ONLY public.campaign_load_steps ADD CONSTRAINT campaign_load_steps_campaign_id_fkey FOREIGN KEY (campaign_id) REFERENCES public.campaigns(id) ON DELETE CASCADE;
ALTER TABLE ONLY public.campaigns ADD CONSTRAINT fk_campaigns_network FOREIGN KEY (network) REFERENCES public.networks(code);
ALTER TABLE ONLY public.executions ADD CONSTRAINT executions_test_id_fkey FOREIGN KEY (test_id) REFERENCES public.tests(id);
ALTER TABLE ONLY public.ipm_files ADD CONSTRAINT ipm_files_execution_id_fkey FOREIGN KEY (execution_id) REFERENCES public.executions(id);
ALTER TABLE ONLY public.ipm_records ADD CONSTRAINT ipm_records_acq_auth_id_fkey FOREIGN KEY (acq_auth_id) REFERENCES public.acq_authorizations(id);
ALTER TABLE ONLY public.ipm_records ADD CONSTRAINT ipm_records_ipm_file_id_fkey FOREIGN KEY (ipm_file_id) REFERENCES public.ipm_files(id) ON DELETE CASCADE;
ALTER TABLE ONLY public.iss_advices ADD CONSTRAINT iss_advices_iss_auth_id_fkey FOREIGN KEY (iss_auth_id) REFERENCES public.iss_authorizations(id);
ALTER TABLE ONLY public.iss_ipm_files ADD CONSTRAINT iss_ipm_files_execution_id_fkey FOREIGN KEY (execution_id) REFERENCES public.executions(id);
ALTER TABLE ONLY public.iss_ipm_records ADD CONSTRAINT iss_ipm_records_ipm_file_id_fkey FOREIGN KEY (ipm_file_id) REFERENCES public.iss_ipm_files(id) ON DELETE CASCADE;
ALTER TABLE ONLY public.iss_reversals ADD CONSTRAINT iss_reversals_iss_auth_id_fkey FOREIGN KEY (iss_auth_id) REFERENCES public.iss_authorizations(id);
ALTER TABLE ONLY public.message_types ADD CONSTRAINT fk_message_types_network FOREIGN KEY (network) REFERENCES public.networks(code);
ALTER TABLE ONLY public.results ADD CONSTRAINT results_execution_id_fkey FOREIGN KEY (execution_id) REFERENCES public.executions(id) ON DELETE CASCADE;
ALTER TABLE ONLY public.role_permissions ADD CONSTRAINT role_permissions_permission_id_fkey FOREIGN KEY (permission_id) REFERENCES public.permissions(id) ON DELETE CASCADE;
ALTER TABLE ONLY public.role_permissions ADD CONSTRAINT role_permissions_role_id_fkey FOREIGN KEY (role_id) REFERENCES public.roles(id) ON DELETE CASCADE;
ALTER TABLE ONLY public.tests ADD CONSTRAINT tests_message_type_id_fkey FOREIGN KEY (message_type_id) REFERENCES public.message_types(id);
ALTER TABLE ONLY public.tps_steps ADD CONSTRAINT tps_steps_test_id_fkey FOREIGN KEY (test_id) REFERENCES public.tests(id) ON DELETE CASCADE;
ALTER TABLE ONLY public.user_tests ADD CONSTRAINT user_tests_test_id_fkey FOREIGN KEY (test_id) REFERENCES public.tests(id) ON DELETE CASCADE;

-- 8. Index
CREATE INDEX IF NOT EXISTS idx_acq_adv_auth ON public.acq_advices USING btree (acq_auth_id);
CREATE INDEX IF NOT EXISTS idx_acq_auth_approved ON public.acq_authorizations USING btree (approved);
CREATE INDEX IF NOT EXISTS idx_acq_auth_de039 ON public.acq_authorizations USING btree (de039_response);
CREATE INDEX IF NOT EXISTS idx_acq_auth_exec ON public.acq_authorizations USING btree (execution_id);
CREATE INDEX IF NOT EXISTS idx_acq_auth_pan ON public.acq_authorizations USING btree (de002_pan);
CREATE INDEX IF NOT EXISTS idx_acq_ipm_files_date ON public.acq_ipm_files USING btree (file_date);
CREATE INDEX IF NOT EXISTS idx_acq_ipm_files_dir ON public.acq_ipm_files USING btree (direction);
CREATE INDEX IF NOT EXISTS idx_acq_ipm_files_exec ON public.acq_ipm_files USING btree (execution_id);
CREATE INDEX IF NOT EXISTS idx_acq_ipm_records_auth ON public.acq_ipm_records USING btree (acq_auth_id);
CREATE INDEX IF NOT EXISTS idx_acq_ipm_records_file ON public.acq_ipm_records USING btree (ipm_file_id);
CREATE INDEX IF NOT EXISTS idx_acq_rev_auth ON public.acq_reversals USING btree (acq_auth_id);
CREATE INDEX IF NOT EXISTS idx_campexecres_de039 ON public.campaign_execution_results USING btree (de039);
CREATE INDEX IF NOT EXISTS idx_campexecres_exec ON public.campaign_execution_results USING btree (execution_id);
CREATE INDEX IF NOT EXISTS idx_campexec_campaign ON public.campaign_executions USING btree (campaign_id);
CREATE INDEX IF NOT EXISTS idx_campexec_status ON public.campaign_executions USING btree (status);
CREATE INDEX IF NOT EXISTS idx_campexec_user ON public.campaign_executions USING btree (user_id);
CREATE INDEX IF NOT EXISTS idx_camploadsteps_campaign ON public.campaign_load_steps USING btree (campaign_id);
CREATE UNIQUE INDEX IF NOT EXISTS uq_dmas_acq_keys ON public.dmas_acq_keys USING btree (member_group_id, key_type, status);
CREATE UNIQUE INDEX IF NOT EXISTS dmas_cards_pan_key ON public.dmas_cards USING btree (pan);
CREATE UNIQUE INDEX IF NOT EXISTS uq_dmas_iss_keys ON public.dmas_iss_keys USING btree (member_group_id, key_type, status);
CREATE INDEX IF NOT EXISTS idx_dmas_kek_group ON public.dmas_kek USING btree (member_group_id);
CREATE UNIQUE INDEX IF NOT EXISTS uq_dmas_kek_group ON public.dmas_kek USING btree (member_group_id);
CREATE INDEX IF NOT EXISTS idx_dmas_tx_pan ON public.dmas_transactions USING btree (pan);
CREATE UNIQUE INDEX IF NOT EXISTS uq_dmas_tx ON public.dmas_transactions USING btree (stan, transmission_dt);
CREATE INDEX IF NOT EXISTS idx_executions_status ON public.executions USING btree (status);
CREATE INDEX IF NOT EXISTS idx_executions_test ON public.executions USING btree (test_id);
CREATE INDEX IF NOT EXISTS idx_executions_user ON public.executions USING btree (user_id);
CREATE INDEX IF NOT EXISTS idx_ipm_files_date ON public.ipm_files USING btree (file_date);
CREATE INDEX IF NOT EXISTS idx_ipm_files_exec ON public.ipm_files USING btree (execution_id);
CREATE INDEX IF NOT EXISTS idx_ipm_log_checksum ON public.ipm_processing_log USING btree (checksum);
CREATE INDEX IF NOT EXISTS idx_ipm_log_exec ON public.ipm_processing_log USING btree (execution_id);
CREATE UNIQUE INDEX IF NOT EXISTS uq_ipm_log ON public.ipm_processing_log USING btree (file_name, role, direction);
CREATE INDEX IF NOT EXISTS idx_ipm_records_auth ON public.ipm_records USING btree (acq_auth_id);
CREATE INDEX IF NOT EXISTS idx_ipm_records_file ON public.ipm_records USING btree (ipm_file_id);
CREATE UNIQUE INDEX IF NOT EXISTS iso_field_catalog_field_code_key ON public.iso_field_catalog USING btree (field_code);
CREATE INDEX IF NOT EXISTS idx_iss_adv_auth ON public.iss_advices USING btree (iss_auth_id);
CREATE INDEX IF NOT EXISTS idx_iss_auth_approved ON public.iss_authorizations USING btree (approved);
CREATE INDEX IF NOT EXISTS idx_iss_auth_pan ON public.iss_authorizations USING btree (de002_pan);
CREATE INDEX IF NOT EXISTS idx_iss_auth_rrn ON public.iss_authorizations USING btree (de037_rrn);
CREATE INDEX IF NOT EXISTS idx_iss_auth_stan ON public.iss_authorizations USING btree (de011_stan);
CREATE INDEX IF NOT EXISTS idx_iss_ipm_files_date ON public.iss_ipm_files USING btree (file_date);
CREATE INDEX IF NOT EXISTS idx_iss_ipm_files_dir ON public.iss_ipm_files USING btree (direction);
CREATE INDEX IF NOT EXISTS idx_iss_ipm_files_exec ON public.iss_ipm_files USING btree (execution_id);
CREATE INDEX IF NOT EXISTS idx_iss_ipm_records_auth ON public.iss_ipm_records USING btree (iss_auth_id);
CREATE INDEX IF NOT EXISTS idx_iss_ipm_records_file ON public.iss_ipm_records USING btree (ipm_file_id);
CREATE INDEX IF NOT EXISTS idx_iss_rev_auth ON public.iss_reversals USING btree (iss_auth_id);
CREATE INDEX IF NOT EXISTS idx_key_store_group ON public.key_store USING btree (member_group_id);
CREATE INDEX IF NOT EXISTS idx_key_store_status ON public.key_store USING btree (status);
CREATE INDEX IF NOT EXISTS idx_key_store_type ON public.key_store USING btree (key_type);
CREATE UNIQUE INDEX IF NOT EXISTS networks_code_key ON public.networks USING btree (code);
CREATE UNIQUE INDEX IF NOT EXISTS permissions_code_key ON public.permissions USING btree (code);
CREATE INDEX IF NOT EXISTS idx_results_de039 ON public.results USING btree (de039);
CREATE INDEX IF NOT EXISTS idx_results_execution ON public.results USING btree (execution_id);
CREATE UNIQUE INDEX IF NOT EXISTS roles_code_key ON public.roles USING btree (code);
CREATE UNIQUE INDEX IF NOT EXISTS uq_swam_acq_keys ON public.swam_acq_keys USING btree (member_group_id, key_type, status);
CREATE INDEX IF NOT EXISTS idx_swam_acq_tx_pan ON public.swam_acq_transactions USING btree (pan);
CREATE UNIQUE INDEX IF NOT EXISTS uq_swam_acq_tx ON public.swam_acq_transactions USING btree (stan, transmission_dt);
CREATE UNIQUE INDEX IF NOT EXISTS swam_cards_pan_key ON public.swam_cards USING btree (pan);
CREATE UNIQUE INDEX IF NOT EXISTS uq_swam_iss_keys ON public.swam_iss_keys USING btree (member_group_id, key_type, status);
CREATE INDEX IF NOT EXISTS idx_swam_iss_tx_pan ON public.swam_iss_transactions USING btree (pan);
CREATE UNIQUE INDEX IF NOT EXISTS uq_swam_iss_tx ON public.swam_iss_transactions USING btree (stan, transmission_dt);
CREATE INDEX IF NOT EXISTS idx_swam_kek_group ON public.swam_kek USING btree (member_group_id);
CREATE UNIQUE INDEX IF NOT EXISTS uq_swam_kek_group ON public.swam_kek USING btree (member_group_id);
CREATE INDEX IF NOT EXISTS idx_user_tests_user ON public.user_tests USING btree (user_id);
CREATE UNIQUE INDEX IF NOT EXISTS users_login_key ON public.users USING btree (login);

-- 9. Controle final
SELECT 'tables total'   AS objet, count(*)::text AS n FROM pg_tables WHERE schemaname='public'
UNION ALL SELECT 'swam_*',        count(*)::text FROM pg_tables WHERE tablename LIKE 'swam%'
UNION ALL SELECT 'users (app)',   count(*)::text FROM users
UNION ALL SELECT 'dmas_cards',    count(*)::text FROM dmas_cards
UNION ALL SELECT 'swam_cards',    count(*)::text FROM swam_cards
UNION ALL SELECT 'networks SWAM', COALESCE(issuer_iso_port::text,'NULL') FROM networks WHERE code='SWAM'
ORDER BY objet;

-- ============================================================
-- Grants SWAM complets (fix installation nouveau PC - session 11)
-- ============================================================
GRANT SELECT, INSERT, UPDATE ON swam_kek              TO swam_issuer_user;
GRANT SELECT, INSERT, UPDATE ON swam_iss_keys         TO swam_issuer_user;
GRANT SELECT, INSERT, UPDATE ON swam_iss_transactions TO swam_issuer_user;
GRANT SELECT, INSERT, UPDATE ON swam_cards            TO swam_issuer_user;
GRANT SELECT                 ON networks              TO swam_issuer_user;

GRANT SELECT, INSERT, UPDATE ON swam_kek              TO swam_acquirer_user;
GRANT SELECT, INSERT, UPDATE ON swam_acq_keys         TO swam_acquirer_user;
GRANT SELECT, INSERT, UPDATE ON swam_acq_transactions TO swam_acquirer_user;
GRANT SELECT                 ON networks              TO swam_acquirer_user;
GRANT SELECT                 ON swam_cards            TO swam_acquirer_user;

GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO swam_issuer_user;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO swam_acquirer_user;

-- ============================================================
-- Grants SWAM complets (fix installation nouveau PC - session 11)
-- ============================================================
GRANT SELECT, INSERT, UPDATE ON swam_kek              TO swam_issuer_user;
GRANT SELECT, INSERT, UPDATE ON swam_iss_keys         TO swam_issuer_user;
GRANT SELECT, INSERT, UPDATE ON swam_iss_transactions TO swam_issuer_user;
GRANT SELECT, INSERT, UPDATE ON swam_cards            TO swam_issuer_user;
GRANT SELECT                 ON networks              TO swam_issuer_user;

GRANT SELECT, INSERT, UPDATE ON swam_kek              TO swam_acquirer_user;
GRANT SELECT, INSERT, UPDATE ON swam_acq_keys         TO swam_acquirer_user;
GRANT SELECT, INSERT, UPDATE ON swam_acq_transactions TO swam_acquirer_user;
GRANT SELECT                 ON networks              TO swam_acquirer_user;
GRANT SELECT                 ON swam_cards            TO swam_acquirer_user;

GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO swam_issuer_user;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO swam_acquirer_user;

-- ============================================================
-- Grants SWAM complets (fix installation nouveau PC - session 11)
-- ============================================================
GRANT SELECT, INSERT, UPDATE ON swam_kek              TO swam_issuer_user;
GRANT SELECT, INSERT, UPDATE ON swam_iss_keys         TO swam_issuer_user;
GRANT SELECT, INSERT, UPDATE ON swam_iss_transactions TO swam_issuer_user;
GRANT SELECT, INSERT, UPDATE ON swam_cards            TO swam_issuer_user;
GRANT SELECT                 ON networks              TO swam_issuer_user;

GRANT SELECT, INSERT, UPDATE ON swam_kek              TO swam_acquirer_user;
GRANT SELECT, INSERT, UPDATE ON swam_acq_keys         TO swam_acquirer_user;
GRANT SELECT, INSERT, UPDATE ON swam_acq_transactions TO swam_acquirer_user;
GRANT SELECT                 ON networks              TO swam_acquirer_user;
GRANT SELECT                 ON swam_cards            TO swam_acquirer_user;

GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO swam_issuer_user;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO swam_acquirer_user;

-- ============================================================
-- Grants SWAM complets (fix installation nouveau PC - session 11)
-- ============================================================
GRANT SELECT, INSERT, UPDATE ON swam_kek              TO swam_issuer_user;
GRANT SELECT, INSERT, UPDATE ON swam_iss_keys         TO swam_issuer_user;
GRANT SELECT, INSERT, UPDATE ON swam_iss_transactions TO swam_issuer_user;
GRANT SELECT, INSERT, UPDATE ON swam_cards            TO swam_issuer_user;
GRANT SELECT                 ON networks              TO swam_issuer_user;

GRANT SELECT, INSERT, UPDATE ON swam_kek              TO swam_acquirer_user;
GRANT SELECT, INSERT, UPDATE ON swam_acq_keys         TO swam_acquirer_user;
GRANT SELECT, INSERT, UPDATE ON swam_acq_transactions TO swam_acquirer_user;
GRANT SELECT                 ON networks              TO swam_acquirer_user;
GRANT SELECT                 ON swam_cards            TO swam_acquirer_user;

GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO swam_issuer_user;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO swam_acquirer_user;
