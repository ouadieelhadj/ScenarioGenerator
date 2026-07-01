--
-- PostgreSQL database dump
--

\restrict 3HVePXo8T9CT2ojdaoTGBOkjT4EOgRbQ7WM4tpO3aqmDyWLqmH2mmrO2dTHzZA4

-- Dumped from database version 18.4
-- Dumped by pg_dump version 18.4

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET transaction_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: acq_advices; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.acq_advices (
    accepted boolean,
    de039_advice_response character varying(2),
    de039_response character varying(2),
    de049_currency character varying(3),
    de060_reason character varying(3),
    duration_ms integer,
    de003_proc_code character varying(6),
    de011_stan character varying(6),
    de038_auth_code character varying(6),
    acq_auth_id bigint,
    de004_amount bigint,
    execution_id bigint,
    id bigint NOT NULL,
    sent_at timestamp(6) without time zone,
    de007_datetime character varying(10),
    de037_rrn character varying(12),
    de002_pan character varying(20),
    request_hex text,
    response_hex text
);


--
-- Name: acq_advices_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.acq_advices_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: acq_advices_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.acq_advices_id_seq OWNED BY public.acq_advices.id;


--
-- Name: acq_authorizations; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.acq_authorizations (
    approved boolean,
    de013_local_date character varying(4),
    de018_mcc character varying(4),
    de022_pos_mode character varying(3),
    de039_response character varying(2),
    de049_currency character varying(3),
    de052_pin_present boolean,
    duration_ms integer,
    ipm_generated boolean,
    de003_proc_code character varying(6),
    de011_stan character varying(6),
    de012_local_time character varying(6),
    de038_auth_code character varying(6),
    de004_amount bigint,
    de041_term_id character varying(8),
    execution_id bigint,
    id bigint NOT NULL,
    ipm_file_id bigint,
    ipm_generated_at timestamp(6) without time zone,
    sent_at timestamp(6) without time zone,
    de007_datetime character varying(10),
    de032_acq_id character varying(11),
    de037_rrn character varying(12),
    de042_merch_id character varying(15),
    de002_pan character varying(20),
    de002_pan_raw character varying(20),
    de043_merch_name character varying(40),
    ipm_file_name character varying(100),
    request_hex text,
    response_hex text
);


--
-- Name: acq_authorizations_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.acq_authorizations_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: acq_authorizations_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.acq_authorizations_id_seq OWNED BY public.acq_authorizations.id;


--
-- Name: acq_ipm_files; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.acq_ipm_files (
    direction character varying(3),
    file_date date NOT NULL,
    nb_transactions integer,
    total_amount_currency character varying(3),
    created_at timestamp(6) without time zone,
    execution_id bigint,
    generation_date timestamp(6) without time zone,
    id bigint NOT NULL,
    total_amount bigint,
    processing_mode character varying(10),
    status character varying(20),
    created_by character varying(50),
    file_id character varying(50),
    file_name character varying(100) NOT NULL,
    file_path_ascii character varying(500),
    file_path_binary character varying(500)
);


--
-- Name: acq_ipm_files_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.acq_ipm_files_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: acq_ipm_files_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.acq_ipm_files_id_seq OWNED BY public.acq_ipm_files.id;


--
-- Name: acq_ipm_records; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.acq_ipm_records (
    de024_func_code character varying(3),
    de025_reason character varying(4),
    de026_mcc character varying(4),
    de049_currency character varying(3),
    de050_currency_recon character varying(3),
    direction character varying(3),
    function_code character varying(3),
    message_number integer,
    mti character varying(4),
    de003_proc_code character varying(6),
    de038_auth_code character varying(6),
    acq_auth_id bigint,
    created_at timestamp(6) without time zone,
    de004_amount bigint,
    de005_amount_recon bigint,
    de030_orig_amount bigint,
    de041_term_id character varying(8),
    de071_msg_num character varying(8),
    id bigint NOT NULL,
    ipm_file_id bigint NOT NULL,
    status character varying(10),
    de032_acq_id character varying(11),
    de012_local_dt character varying(12),
    de022_pos_code character varying(12),
    de037_rrn character varying(12),
    de042_merch_id character varying(15),
    record_type character varying(15),
    de002_pan character varying(20),
    de031_acq_ref_data character varying(23),
    de043_merch_name character varying(40),
    de063_network_data character varying(50),
    de072_data_record character varying(255),
    error_message character varying(255),
    pds_data text,
    raw_ascii text,
    raw_hex text
);


--
-- Name: acq_ipm_records_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.acq_ipm_records_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: acq_ipm_records_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.acq_ipm_records_id_seq OWNED BY public.acq_ipm_records.id;


--
-- Name: acq_reversals; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.acq_reversals (
    de039_original character varying(2),
    de039_response character varying(2),
    de049_currency character varying(3),
    duration_ms integer,
    reversed boolean,
    de003_proc_code character varying(6),
    de011_stan character varying(6),
    de038_auth_code character varying(6),
    acq_auth_id bigint,
    de004_amount bigint,
    de041_term_id character varying(8),
    execution_id bigint,
    id bigint NOT NULL,
    sent_at timestamp(6) without time zone,
    de007_datetime character varying(10),
    de037_rrn character varying(12),
    de042_merch_id character varying(15),
    de002_pan character varying(20),
    de056_orig_data character varying(40),
    request_hex text,
    response_hex text
);


--
-- Name: acq_reversals_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.acq_reversals_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: acq_reversals_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.acq_reversals_id_seq OWNED BY public.acq_reversals.id;


--
-- Name: bin_range; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.bin_range (
    enabled boolean NOT NULL,
    is_range boolean NOT NULL,
    pan_length integer NOT NULL,
    id bigint NOT NULL,
    code character varying(20) NOT NULL,
    network character varying(20) NOT NULL,
    product_name character varying(60) NOT NULL
);


--
-- Name: bin_range_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.bin_range_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: bin_range_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.bin_range_id_seq OWNED BY public.bin_range.id;


--
-- Name: campaign_execution_results; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.campaign_execution_results (
    approved boolean,
    de039 character varying(2),
    duration_ms integer,
    step_order integer,
    de038_auth_code character varying(6),
    executed_at timestamp(6) without time zone,
    execution_id bigint NOT NULL,
    id bigint NOT NULL,
    pan_masked character varying(20),
    request_hex text,
    response_hex text
);


--
-- Name: campaign_execution_results_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.campaign_execution_results_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: campaign_execution_results_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.campaign_execution_results_id_seq OWNED BY public.campaign_execution_results.id;


--
-- Name: campaign_executions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.campaign_executions (
    duration_seconds integer,
    response_time_avg numeric(10,2),
    response_time_max numeric(10,2),
    response_time_min numeric(10,2),
    response_time_p95 numeric(10,2),
    response_time_p99 numeric(10,2),
    tps_actual_avg numeric(10,2),
    tps_target integer,
    tx_approved integer,
    tx_declined integer,
    tx_sent integer,
    tx_total integer,
    campaign_id bigint NOT NULL,
    ended_at timestamp(6) without time zone,
    id bigint NOT NULL,
    started_at timestamp(6) without time zone,
    user_id bigint NOT NULL,
    verdict character varying(10),
    status character varying(20) NOT NULL,
    report_dir character varying(255),
    report_excel character varying(255),
    report_pdf character varying(255),
    verdict_detail character varying(255)
);


--
-- Name: campaign_executions_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.campaign_executions_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: campaign_executions_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.campaign_executions_id_seq OWNED BY public.campaign_executions.id;


--
-- Name: campaign_load_steps; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.campaign_load_steps (
    concurrency integer,
    end_seconds integer NOT NULL,
    start_seconds integer NOT NULL,
    step_order integer NOT NULL,
    tps_value integer NOT NULL,
    campaign_id bigint NOT NULL,
    id bigint NOT NULL
);


--
-- Name: campaign_load_steps_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.campaign_load_steps_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: campaign_load_steps_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.campaign_load_steps_id_seq OWNED BY public.campaign_load_steps.id;


--
-- Name: campaigns; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.campaigns (
    active boolean NOT NULL,
    expected_de039 character varying(2),
    sla_approval_min numeric(5,2),
    sla_error_rate_max numeric(5,2),
    sla_p95_max_ms integer,
    stop_on_error_rate numeric(5,2),
    created_at timestamp(6) without time zone,
    created_by bigint,
    id bigint NOT NULL,
    category character varying(50),
    name character varying(100) NOT NULL,
    config text,
    description character varying(255)
);


--
-- Name: campaigns_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.campaigns_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: campaigns_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.campaigns_id_seq OWNED BY public.campaigns.id;


--
-- Name: dmas_acq_keys; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.dmas_acq_keys (
    key_length integer,
    key_type character varying(3),
    kcv character varying(6),
    created_at timestamp(6) without time zone,
    id bigint NOT NULL,
    status character varying(10),
    member_group_id character varying(20),
    key_under_kek character varying(64),
    key_under_lmk character varying(64)
);


--
-- Name: dmas_acq_keys_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.dmas_acq_keys_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: dmas_acq_keys_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.dmas_acq_keys_id_seq OWNED BY public.dmas_acq_keys.id;


--
-- Name: dmas_cards; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.dmas_cards (
    currency character varying(3),
    expiry character varying(4),
    balance bigint,
    created_at timestamp(6) without time zone,
    id bigint NOT NULL,
    updated_at timestamp(6) without time zone,
    status character varying(10),
    pin character varying(12),
    pan character varying(19)
);


--
-- Name: dmas_cards_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.dmas_cards_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: dmas_cards_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.dmas_cards_id_seq OWNED BY public.dmas_cards.id;


--
-- Name: dmas_iss_keys; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.dmas_iss_keys (
    key_length integer,
    key_type character varying(3),
    kcv character varying(6),
    created_at timestamp(6) without time zone,
    id bigint NOT NULL,
    status character varying(10),
    member_group_id character varying(20),
    key_under_kek character varying(64),
    key_under_lmk character varying(64)
);


--
-- Name: dmas_iss_keys_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.dmas_iss_keys_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: dmas_iss_keys_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.dmas_iss_keys_id_seq OWNED BY public.dmas_iss_keys.id;


--
-- Name: dmas_kek; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.dmas_kek (
    key_length integer,
    kcv character varying(6),
    created_at timestamp(6) without time zone,
    id bigint NOT NULL,
    status character varying(10),
    member_group_id character varying(20),
    kek_clear character varying(48),
    kek_under_acq_lmk character varying(128),
    kek_under_iss_lmk character varying(128),
    description character varying(255)
);


--
-- Name: dmas_kek_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.dmas_kek_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: dmas_kek_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.dmas_kek_id_seq OWNED BY public.dmas_kek.id;


--
-- Name: dmas_transactions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.dmas_transactions (
    currency character varying(3),
    mti character varying(4),
    response_code character varying(2),
    processing_code character varying(6),
    amount bigint,
    created_at timestamp(6) without time zone,
    id bigint NOT NULL,
    reversed_at timestamp(6) without time zone,
    status character varying(10),
    transmission_dt character varying(10),
    stan character varying(12),
    pan character varying(19)
);


--
-- Name: dmas_transactions_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.dmas_transactions_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: dmas_transactions_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.dmas_transactions_id_seq OWNED BY public.dmas_transactions.id;


--
-- Name: executions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.executions (
    duration_seconds integer,
    response_time_avg numeric(10,2),
    response_time_max numeric(10,2),
    response_time_min numeric(10,2),
    response_time_p95 numeric(10,2),
    response_time_p99 numeric(10,2),
    tps_actual_avg numeric(10,2),
    tps_target integer,
    tx_approved integer,
    tx_declined integer,
    tx_sent integer,
    tx_total integer,
    ended_at timestamp(6) without time zone,
    id bigint NOT NULL,
    started_at timestamp(6) without time zone,
    test_id bigint NOT NULL,
    user_id bigint NOT NULL,
    mode character varying(20) NOT NULL,
    status character varying(20) NOT NULL,
    report_dir character varying(255),
    report_excel character varying(255),
    report_pdf character varying(255),
    CONSTRAINT executions_mode_check CHECK (((mode)::text = ANY ((ARRAY['SIMPLE'::character varying, 'CHARGE'::character varying])::text[]))),
    CONSTRAINT executions_status_check CHECK (((status)::text = ANY ((ARRAY['RUNNING'::character varying, 'COMPLETED'::character varying, 'STOPPED'::character varying, 'ERROR'::character varying])::text[])))
);


--
-- Name: executions_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.executions_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: executions_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.executions_id_seq OWNED BY public.executions.id;


--
-- Name: ipm_files; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.ipm_files (
    file_date date,
    nb_transactions integer,
    total_amount_currency character varying(3),
    created_at timestamp(6) without time zone,
    execution_id bigint,
    generation_date timestamp(6) without time zone,
    id bigint NOT NULL,
    total_amount bigint,
    processing_mode character varying(10),
    status character varying(20),
    created_by character varying(50),
    file_id character varying(50),
    file_name character varying(100),
    file_path_ascii character varying(500),
    file_path_binary character varying(500)
);


--
-- Name: ipm_files_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.ipm_files_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: ipm_files_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.ipm_files_id_seq OWNED BY public.ipm_files.id;


--
-- Name: ipm_processing_log; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.ipm_processing_log (
    direction character varying(3),
    record_count integer,
    execution_id bigint,
    id bigint NOT NULL,
    processed_at timestamp(6) without time zone,
    role character varying(10),
    action character varying(15),
    status character varying(15),
    file_id character varying(50),
    checksum character varying(64),
    file_name character varying(100),
    file_path character varying(500)
);


--
-- Name: ipm_processing_log_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.ipm_processing_log_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: ipm_processing_log_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.ipm_processing_log_id_seq OWNED BY public.ipm_processing_log.id;


--
-- Name: ipm_records; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.ipm_records (
    de024_func_code character varying(3),
    de025_reason character varying(4),
    de026_mcc character varying(4),
    de049_currency character varying(3),
    de050_currency_recon character varying(3),
    function_code character varying(3),
    message_number integer,
    mti character varying(4),
    de003_proc_code character varying(6),
    de038_auth_code character varying(6),
    acq_auth_id bigint,
    created_at timestamp(6) without time zone,
    de004_amount bigint,
    de005_amount_recon bigint,
    de041_term_id character varying(8),
    de071_msg_num character varying(8),
    id bigint NOT NULL,
    ipm_file_id bigint NOT NULL,
    status character varying(10),
    de032_acq_id character varying(11),
    de012_local_dt character varying(12),
    de022_pos_code character varying(12),
    de037_rrn character varying(12),
    de042_merch_id character varying(15),
    record_type character varying(15),
    de002_pan character varying(20),
    de031_acq_ref_data character varying(23),
    de043_merch_name character varying(40),
    de063_network_data character varying(50),
    error_message character varying(255),
    raw_ascii text,
    raw_hex text
);


--
-- Name: ipm_records_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.ipm_records_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: ipm_records_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.ipm_records_id_seq OWNED BY public.ipm_records.id;


--
-- Name: iso_field_catalog; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.iso_field_catalog (
    display_order integer NOT NULL,
    enabled boolean NOT NULL,
    id bigint NOT NULL,
    field_code character varying(10) NOT NULL,
    gen_strategy character varying(40) NOT NULL,
    name character varying(60) NOT NULL,
    description character varying(255)
);


--
-- Name: iso_field_catalog_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.iso_field_catalog_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: iso_field_catalog_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.iso_field_catalog_id_seq OWNED BY public.iso_field_catalog.id;


--
-- Name: iss_advices; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.iss_advices (
    accepted boolean,
    de039_advice_response character varying(2),
    de039_response character varying(2),
    de049_currency character varying(3),
    de060_reason character varying(3),
    de003_proc_code character varying(6),
    de011_stan character varying(6),
    de038_auth_code character varying(6),
    de004_amount bigint,
    id bigint NOT NULL,
    iss_auth_id bigint,
    received_at timestamp(6) without time zone,
    de007_datetime character varying(10),
    de037_rrn character varying(12),
    de002_pan character varying(20),
    request_hex text,
    response_hex text
);


--
-- Name: iss_advices_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.iss_advices_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: iss_advices_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.iss_advices_id_seq OWNED BY public.iss_advices.id;


--
-- Name: iss_authorizations; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.iss_authorizations (
    approved boolean,
    de013_local_date character varying(4),
    de018_mcc character varying(4),
    de022_pos_mode character varying(3),
    de039_response character varying(2),
    de049_currency character varying(3),
    de052_pin_present boolean,
    ipm_generated boolean,
    mac_verified boolean,
    de003_proc_code character varying(6),
    de011_stan character varying(6),
    de012_local_time character varying(6),
    de038_auth_code character varying(6),
    de004_amount bigint,
    de041_term_id character varying(8),
    id bigint NOT NULL,
    ipm_file_id bigint,
    ipm_generated_at timestamp(6) without time zone,
    received_at timestamp(6) without time zone,
    responded_at timestamp(6) without time zone,
    de007_datetime character varying(10),
    de032_acq_id character varying(11),
    de037_rrn character varying(12),
    de042_merch_id character varying(15),
    de002_pan_raw character varying(19),
    de002_pan character varying(20),
    de043_merch_name character varying(40),
    decision_reason character varying(100),
    ipm_file_name character varying(100),
    request_hex text,
    response_hex text
);


--
-- Name: iss_authorizations_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.iss_authorizations_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: iss_authorizations_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.iss_authorizations_id_seq OWNED BY public.iss_authorizations.id;


--
-- Name: iss_ipm_files; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.iss_ipm_files (
    direction character varying(3),
    file_date date NOT NULL,
    nb_transactions integer,
    total_amount_currency character varying(3),
    created_at timestamp(6) without time zone,
    execution_id bigint,
    generation_date timestamp(6) without time zone,
    id bigint NOT NULL,
    total_amount bigint,
    processing_mode character varying(10),
    status character varying(20),
    created_by character varying(50),
    file_id character varying(50),
    file_name character varying(100) NOT NULL,
    file_path_ascii character varying(500),
    file_path_binary character varying(500)
);


--
-- Name: iss_ipm_files_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.iss_ipm_files_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: iss_ipm_files_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.iss_ipm_files_id_seq OWNED BY public.iss_ipm_files.id;


--
-- Name: iss_ipm_records; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.iss_ipm_records (
    de024_func_code character varying(3),
    de025_reason character varying(4),
    de026_mcc character varying(4),
    de049_currency character varying(3),
    de050_currency_recon character varying(3),
    direction character varying(3),
    function_code character varying(3),
    message_number integer,
    mti character varying(4),
    de003_proc_code character varying(6),
    de038_auth_code character varying(6),
    created_at timestamp(6) without time zone,
    de004_amount bigint,
    de005_amount_recon bigint,
    de030_orig_amount bigint,
    de041_term_id character varying(8),
    de071_msg_num character varying(8),
    id bigint NOT NULL,
    ipm_file_id bigint NOT NULL,
    iss_auth_id bigint,
    status character varying(10),
    de032_acq_id character varying(11),
    de093_dest_id character varying(11),
    de094_origin_id character varying(11),
    de012_local_dt character varying(12),
    de022_pos_code character varying(12),
    de037_rrn character varying(12),
    de042_merch_id character varying(15),
    record_type character varying(15),
    de002_pan character varying(20),
    de031_acq_ref_data character varying(23),
    de043_merch_name character varying(40),
    de063_network_data character varying(50),
    de072_data_record character varying(255),
    error_message character varying(255),
    pds_data text,
    raw_ascii text,
    raw_hex text
);


--
-- Name: iss_ipm_records_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.iss_ipm_records_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: iss_ipm_records_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.iss_ipm_records_id_seq OWNED BY public.iss_ipm_records.id;


--
-- Name: iss_reversals; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.iss_reversals (
    de039_original character varying(2),
    de039_response character varying(2),
    de049_currency character varying(3),
    reversed boolean,
    de003_proc_code character varying(6),
    de011_stan character varying(6),
    de038_auth_code character varying(6),
    de004_amount bigint,
    de041_term_id character varying(8),
    id bigint NOT NULL,
    iss_auth_id bigint,
    received_at timestamp(6) without time zone,
    de007_datetime character varying(10),
    de037_rrn character varying(12),
    de002_pan character varying(20),
    de056_orig_data character varying(40),
    request_hex text,
    response_hex text
);


--
-- Name: iss_reversals_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.iss_reversals_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: iss_reversals_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.iss_reversals_id_seq OWNED BY public.iss_reversals.id;


--
-- Name: key_store; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.key_store (
    key_length integer,
    key_type character varying(3),
    kcv character varying(6),
    activated_at timestamp(6) without time zone,
    created_at timestamp(6) without time zone,
    id bigint NOT NULL,
    status character varying(10),
    member_group_id character varying(20),
    encrypted_value character varying(64),
    description character varying(255)
);


--
-- Name: key_store_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.key_store_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: key_store_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.key_store_id_seq OWNED BY public.key_store.id;


--
-- Name: message_types; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.message_types (
    active boolean NOT NULL,
    code character varying(4) NOT NULL,
    created_at timestamp(6) without time zone,
    id bigint NOT NULL,
    category character varying(50) NOT NULL,
    name character varying(100) NOT NULL,
    description character varying(255),
    processing_codes text
);


--
-- Name: message_types_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.message_types_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: message_types_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.message_types_id_seq OWNED BY public.message_types.id;


--
-- Name: permissions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.permissions (
    id bigint NOT NULL,
    category character varying(50) NOT NULL,
    code character varying(50) NOT NULL,
    label character varying(100) NOT NULL
);


--
-- Name: permissions_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.permissions_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: permissions_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.permissions_id_seq OWNED BY public.permissions.id;


--
-- Name: results; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.results (
    approved boolean,
    de039 character varying(2),
    duration_ms integer,
    de038_auth_code character varying(6),
    executed_at timestamp(6) without time zone,
    execution_id bigint NOT NULL,
    id bigint NOT NULL,
    pan_masked character varying(20),
    request_hex text,
    response_hex text
);


--
-- Name: results_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.results_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: results_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.results_id_seq OWNED BY public.results.id;


--
-- Name: role_permissions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.role_permissions (
    permission_id bigint NOT NULL,
    role_id bigint NOT NULL
);


--
-- Name: roles; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.roles (
    id bigint NOT NULL,
    code character varying(30) NOT NULL,
    label character varying(100) NOT NULL,
    description character varying(255)
);


--
-- Name: roles_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.roles_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: roles_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.roles_id_seq OWNED BY public.roles.id;


--
-- Name: tests; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.tests (
    active boolean NOT NULL,
    expected_de039 character varying(2),
    created_at timestamp(6) without time zone,
    created_by bigint,
    id bigint NOT NULL,
    message_type_id bigint,
    category character varying(50),
    name character varying(100) NOT NULL,
    config text,
    description character varying(255)
);


--
-- Name: tests_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.tests_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: tests_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.tests_id_seq OWNED BY public.tests.id;


--
-- Name: tps_steps; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.tps_steps (
    end_seconds integer NOT NULL,
    start_seconds integer NOT NULL,
    step_order integer NOT NULL,
    tps_value integer NOT NULL,
    id bigint NOT NULL,
    test_id bigint NOT NULL
);


--
-- Name: tps_steps_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.tps_steps_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: tps_steps_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.tps_steps_id_seq OWNED BY public.tps_steps.id;


--
-- Name: user_tests; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.user_tests (
    test_id bigint NOT NULL,
    user_id bigint NOT NULL
);


--
-- Name: users; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.users (
    active boolean NOT NULL,
    created_at timestamp(6) without time zone,
    id bigint NOT NULL,
    last_login timestamp(6) without time zone,
    role character varying(30) NOT NULL,
    created_by character varying(50),
    login character varying(50) NOT NULL,
    email character varying(100),
    password character varying(255) NOT NULL
);


--
-- Name: users_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.users_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: users_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.users_id_seq OWNED BY public.users.id;


--
-- Name: acq_advices id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.acq_advices ALTER COLUMN id SET DEFAULT nextval('public.acq_advices_id_seq'::regclass);


--
-- Name: acq_authorizations id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.acq_authorizations ALTER COLUMN id SET DEFAULT nextval('public.acq_authorizations_id_seq'::regclass);


--
-- Name: acq_ipm_files id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.acq_ipm_files ALTER COLUMN id SET DEFAULT nextval('public.acq_ipm_files_id_seq'::regclass);


--
-- Name: acq_ipm_records id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.acq_ipm_records ALTER COLUMN id SET DEFAULT nextval('public.acq_ipm_records_id_seq'::regclass);


--
-- Name: acq_reversals id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.acq_reversals ALTER COLUMN id SET DEFAULT nextval('public.acq_reversals_id_seq'::regclass);


--
-- Name: bin_range id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.bin_range ALTER COLUMN id SET DEFAULT nextval('public.bin_range_id_seq'::regclass);


--
-- Name: campaign_execution_results id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.campaign_execution_results ALTER COLUMN id SET DEFAULT nextval('public.campaign_execution_results_id_seq'::regclass);


--
-- Name: campaign_executions id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.campaign_executions ALTER COLUMN id SET DEFAULT nextval('public.campaign_executions_id_seq'::regclass);


--
-- Name: campaign_load_steps id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.campaign_load_steps ALTER COLUMN id SET DEFAULT nextval('public.campaign_load_steps_id_seq'::regclass);


--
-- Name: campaigns id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.campaigns ALTER COLUMN id SET DEFAULT nextval('public.campaigns_id_seq'::regclass);


--
-- Name: dmas_acq_keys id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.dmas_acq_keys ALTER COLUMN id SET DEFAULT nextval('public.dmas_acq_keys_id_seq'::regclass);


--
-- Name: dmas_cards id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.dmas_cards ALTER COLUMN id SET DEFAULT nextval('public.dmas_cards_id_seq'::regclass);


--
-- Name: dmas_iss_keys id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.dmas_iss_keys ALTER COLUMN id SET DEFAULT nextval('public.dmas_iss_keys_id_seq'::regclass);


--
-- Name: dmas_kek id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.dmas_kek ALTER COLUMN id SET DEFAULT nextval('public.dmas_kek_id_seq'::regclass);


--
-- Name: dmas_transactions id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.dmas_transactions ALTER COLUMN id SET DEFAULT nextval('public.dmas_transactions_id_seq'::regclass);


--
-- Name: executions id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.executions ALTER COLUMN id SET DEFAULT nextval('public.executions_id_seq'::regclass);


--
-- Name: ipm_files id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ipm_files ALTER COLUMN id SET DEFAULT nextval('public.ipm_files_id_seq'::regclass);


--
-- Name: ipm_processing_log id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ipm_processing_log ALTER COLUMN id SET DEFAULT nextval('public.ipm_processing_log_id_seq'::regclass);


--
-- Name: ipm_records id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ipm_records ALTER COLUMN id SET DEFAULT nextval('public.ipm_records_id_seq'::regclass);


--
-- Name: iso_field_catalog id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.iso_field_catalog ALTER COLUMN id SET DEFAULT nextval('public.iso_field_catalog_id_seq'::regclass);


--
-- Name: iss_advices id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.iss_advices ALTER COLUMN id SET DEFAULT nextval('public.iss_advices_id_seq'::regclass);


--
-- Name: iss_authorizations id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.iss_authorizations ALTER COLUMN id SET DEFAULT nextval('public.iss_authorizations_id_seq'::regclass);


--
-- Name: iss_ipm_files id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.iss_ipm_files ALTER COLUMN id SET DEFAULT nextval('public.iss_ipm_files_id_seq'::regclass);


--
-- Name: iss_ipm_records id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.iss_ipm_records ALTER COLUMN id SET DEFAULT nextval('public.iss_ipm_records_id_seq'::regclass);


--
-- Name: iss_reversals id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.iss_reversals ALTER COLUMN id SET DEFAULT nextval('public.iss_reversals_id_seq'::regclass);


--
-- Name: key_store id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.key_store ALTER COLUMN id SET DEFAULT nextval('public.key_store_id_seq'::regclass);


--
-- Name: message_types id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.message_types ALTER COLUMN id SET DEFAULT nextval('public.message_types_id_seq'::regclass);


--
-- Name: permissions id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.permissions ALTER COLUMN id SET DEFAULT nextval('public.permissions_id_seq'::regclass);


--
-- Name: results id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.results ALTER COLUMN id SET DEFAULT nextval('public.results_id_seq'::regclass);


--
-- Name: roles id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.roles ALTER COLUMN id SET DEFAULT nextval('public.roles_id_seq'::regclass);


--
-- Name: tests id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tests ALTER COLUMN id SET DEFAULT nextval('public.tests_id_seq'::regclass);


--
-- Name: tps_steps id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tps_steps ALTER COLUMN id SET DEFAULT nextval('public.tps_steps_id_seq'::regclass);


--
-- Name: users id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users ALTER COLUMN id SET DEFAULT nextval('public.users_id_seq'::regclass);


--
-- Name: acq_advices acq_advices_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.acq_advices
    ADD CONSTRAINT acq_advices_pkey PRIMARY KEY (id);


--
-- Name: acq_authorizations acq_authorizations_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.acq_authorizations
    ADD CONSTRAINT acq_authorizations_pkey PRIMARY KEY (id);


--
-- Name: acq_ipm_files acq_ipm_files_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.acq_ipm_files
    ADD CONSTRAINT acq_ipm_files_pkey PRIMARY KEY (id);


--
-- Name: acq_ipm_records acq_ipm_records_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.acq_ipm_records
    ADD CONSTRAINT acq_ipm_records_pkey PRIMARY KEY (id);


--
-- Name: acq_reversals acq_reversals_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.acq_reversals
    ADD CONSTRAINT acq_reversals_pkey PRIMARY KEY (id);


--
-- Name: bin_range bin_range_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.bin_range
    ADD CONSTRAINT bin_range_pkey PRIMARY KEY (id);


--
-- Name: campaign_execution_results campaign_execution_results_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.campaign_execution_results
    ADD CONSTRAINT campaign_execution_results_pkey PRIMARY KEY (id);


--
-- Name: campaign_executions campaign_executions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.campaign_executions
    ADD CONSTRAINT campaign_executions_pkey PRIMARY KEY (id);


--
-- Name: campaign_load_steps campaign_load_steps_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.campaign_load_steps
    ADD CONSTRAINT campaign_load_steps_pkey PRIMARY KEY (id);


--
-- Name: campaigns campaigns_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.campaigns
    ADD CONSTRAINT campaigns_pkey PRIMARY KEY (id);


--
-- Name: dmas_acq_keys dmas_acq_keys_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.dmas_acq_keys
    ADD CONSTRAINT dmas_acq_keys_pkey PRIMARY KEY (id);


--
-- Name: dmas_cards dmas_cards_pan_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.dmas_cards
    ADD CONSTRAINT dmas_cards_pan_key UNIQUE (pan);


--
-- Name: dmas_cards dmas_cards_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.dmas_cards
    ADD CONSTRAINT dmas_cards_pkey PRIMARY KEY (id);


--
-- Name: dmas_iss_keys dmas_iss_keys_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.dmas_iss_keys
    ADD CONSTRAINT dmas_iss_keys_pkey PRIMARY KEY (id);


--
-- Name: dmas_kek dmas_kek_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.dmas_kek
    ADD CONSTRAINT dmas_kek_pkey PRIMARY KEY (id);


--
-- Name: dmas_transactions dmas_transactions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.dmas_transactions
    ADD CONSTRAINT dmas_transactions_pkey PRIMARY KEY (id);


--
-- Name: executions executions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.executions
    ADD CONSTRAINT executions_pkey PRIMARY KEY (id);


--
-- Name: ipm_files ipm_files_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ipm_files
    ADD CONSTRAINT ipm_files_pkey PRIMARY KEY (id);


--
-- Name: ipm_processing_log ipm_processing_log_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ipm_processing_log
    ADD CONSTRAINT ipm_processing_log_pkey PRIMARY KEY (id);


--
-- Name: ipm_records ipm_records_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ipm_records
    ADD CONSTRAINT ipm_records_pkey PRIMARY KEY (id);


--
-- Name: iso_field_catalog iso_field_catalog_field_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.iso_field_catalog
    ADD CONSTRAINT iso_field_catalog_field_code_key UNIQUE (field_code);


--
-- Name: iso_field_catalog iso_field_catalog_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.iso_field_catalog
    ADD CONSTRAINT iso_field_catalog_pkey PRIMARY KEY (id);


--
-- Name: iss_advices iss_advices_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.iss_advices
    ADD CONSTRAINT iss_advices_pkey PRIMARY KEY (id);


--
-- Name: iss_authorizations iss_authorizations_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.iss_authorizations
    ADD CONSTRAINT iss_authorizations_pkey PRIMARY KEY (id);


--
-- Name: iss_ipm_files iss_ipm_files_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.iss_ipm_files
    ADD CONSTRAINT iss_ipm_files_pkey PRIMARY KEY (id);


--
-- Name: iss_ipm_records iss_ipm_records_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.iss_ipm_records
    ADD CONSTRAINT iss_ipm_records_pkey PRIMARY KEY (id);


--
-- Name: iss_reversals iss_reversals_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.iss_reversals
    ADD CONSTRAINT iss_reversals_pkey PRIMARY KEY (id);


--
-- Name: key_store key_store_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.key_store
    ADD CONSTRAINT key_store_pkey PRIMARY KEY (id);


--
-- Name: message_types message_types_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.message_types
    ADD CONSTRAINT message_types_pkey PRIMARY KEY (id);


--
-- Name: permissions permissions_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.permissions
    ADD CONSTRAINT permissions_code_key UNIQUE (code);


--
-- Name: permissions permissions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.permissions
    ADD CONSTRAINT permissions_pkey PRIMARY KEY (id);


--
-- Name: results results_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.results
    ADD CONSTRAINT results_pkey PRIMARY KEY (id);


--
-- Name: role_permissions role_permissions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.role_permissions
    ADD CONSTRAINT role_permissions_pkey PRIMARY KEY (permission_id, role_id);


--
-- Name: roles roles_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.roles
    ADD CONSTRAINT roles_code_key UNIQUE (code);


--
-- Name: roles roles_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.roles
    ADD CONSTRAINT roles_pkey PRIMARY KEY (id);


--
-- Name: tests tests_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tests
    ADD CONSTRAINT tests_pkey PRIMARY KEY (id);


--
-- Name: tps_steps tps_steps_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tps_steps
    ADD CONSTRAINT tps_steps_pkey PRIMARY KEY (id);


--
-- Name: user_tests user_tests_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_tests
    ADD CONSTRAINT user_tests_pkey PRIMARY KEY (test_id, user_id);


--
-- Name: users users_login_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_login_key UNIQUE (login);


--
-- Name: users users_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_pkey PRIMARY KEY (id);


--
-- Name: tests fk1oy80cdks1pyyw0sid32mc0o; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tests
    ADD CONSTRAINT fk1oy80cdks1pyyw0sid32mc0o FOREIGN KEY (created_by) REFERENCES public.users(id);


--
-- Name: ipm_records fk293dh8vw3x5y2t26g4st0fqpj; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ipm_records
    ADD CONSTRAINT fk293dh8vw3x5y2t26g4st0fqpj FOREIGN KEY (acq_auth_id) REFERENCES public.acq_authorizations(id);


--
-- Name: iss_reversals fk3sek0sxebf0d08kiyx2axfley; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.iss_reversals
    ADD CONSTRAINT fk3sek0sxebf0d08kiyx2axfley FOREIGN KEY (iss_auth_id) REFERENCES public.iss_authorizations(id);


--
-- Name: campaign_execution_results fk5i37oxxvm45msfis9htu2jx0x; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.campaign_execution_results
    ADD CONSTRAINT fk5i37oxxvm45msfis9htu2jx0x FOREIGN KEY (execution_id) REFERENCES public.campaign_executions(id);


--
-- Name: iss_ipm_records fk624cqp0p55wb9cff258mjtsm0; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.iss_ipm_records
    ADD CONSTRAINT fk624cqp0p55wb9cff258mjtsm0 FOREIGN KEY (ipm_file_id) REFERENCES public.iss_ipm_files(id);


--
-- Name: user_tests fk8e9rp1n7lsc6lnagryltqpbjb; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_tests
    ADD CONSTRAINT fk8e9rp1n7lsc6lnagryltqpbjb FOREIGN KEY (user_id) REFERENCES public.users(id);


--
-- Name: campaign_executions fk8y5abir912bf39schnlg7k1wy; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.campaign_executions
    ADD CONSTRAINT fk8y5abir912bf39schnlg7k1wy FOREIGN KEY (campaign_id) REFERENCES public.campaigns(id);


--
-- Name: tps_steps fkaoji7qobpqtd6eplyq6aqaaq5; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tps_steps
    ADD CONSTRAINT fkaoji7qobpqtd6eplyq6aqaaq5 FOREIGN KEY (test_id) REFERENCES public.tests(id);


--
-- Name: acq_ipm_records fkbb0xqmr8jq8agte52ghudc1pk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.acq_ipm_records
    ADD CONSTRAINT fkbb0xqmr8jq8agte52ghudc1pk FOREIGN KEY (ipm_file_id) REFERENCES public.acq_ipm_files(id);


--
-- Name: ipm_records fkcv0yptkdgd1mhxvb10w7jyucs; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ipm_records
    ADD CONSTRAINT fkcv0yptkdgd1mhxvb10w7jyucs FOREIGN KEY (ipm_file_id) REFERENCES public.ipm_files(id);


--
-- Name: acq_reversals fke0vsflnu6s0qo1t4m1v2iwchs; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.acq_reversals
    ADD CONSTRAINT fke0vsflnu6s0qo1t4m1v2iwchs FOREIGN KEY (execution_id) REFERENCES public.executions(id);


--
-- Name: role_permissions fkegdk29eiy7mdtefy5c7eirr6e; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.role_permissions
    ADD CONSTRAINT fkegdk29eiy7mdtefy5c7eirr6e FOREIGN KEY (permission_id) REFERENCES public.permissions(id);


--
-- Name: iss_advices fkf0kp4vealr0eb7efo1fymbx52; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.iss_advices
    ADD CONSTRAINT fkf0kp4vealr0eb7efo1fymbx52 FOREIGN KEY (iss_auth_id) REFERENCES public.iss_authorizations(id);


--
-- Name: executions fkj849cchf4syfvvy9hacyqhcsf; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.executions
    ADD CONSTRAINT fkj849cchf4syfvvy9hacyqhcsf FOREIGN KEY (test_id) REFERENCES public.tests(id);


--
-- Name: acq_advices fkjpiq35udtkjbcpus8mamibxuy; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.acq_advices
    ADD CONSTRAINT fkjpiq35udtkjbcpus8mamibxuy FOREIGN KEY (execution_id) REFERENCES public.executions(id);


--
-- Name: tests fkjyqt67yptgyvrbbt1agotjn8i; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tests
    ADD CONSTRAINT fkjyqt67yptgyvrbbt1agotjn8i FOREIGN KEY (message_type_id) REFERENCES public.message_types(id);


--
-- Name: campaign_executions fkk0j346ahbh7qcd56pxm72022l; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.campaign_executions
    ADD CONSTRAINT fkk0j346ahbh7qcd56pxm72022l FOREIGN KEY (user_id) REFERENCES public.users(id);


--
-- Name: acq_authorizations fkkhfg3cmryopr8kfs4tc0m6u9p; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.acq_authorizations
    ADD CONSTRAINT fkkhfg3cmryopr8kfs4tc0m6u9p FOREIGN KEY (execution_id) REFERENCES public.executions(id);


--
-- Name: campaign_load_steps fkmaac7et0k2wkm42qqfcpstryj; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.campaign_load_steps
    ADD CONSTRAINT fkmaac7et0k2wkm42qqfcpstryj FOREIGN KEY (campaign_id) REFERENCES public.campaigns(id);


--
-- Name: role_permissions fkn5fotdgk8d1xvo8nav9uv3muc; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.role_permissions
    ADD CONSTRAINT fkn5fotdgk8d1xvo8nav9uv3muc FOREIGN KEY (role_id) REFERENCES public.roles(id);


--
-- Name: ipm_files fknnbscm6h1ml2m4354y0oub1q7; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ipm_files
    ADD CONSTRAINT fknnbscm6h1ml2m4354y0oub1q7 FOREIGN KEY (execution_id) REFERENCES public.executions(id);


--
-- Name: results fknp9gjedasyck4yaywivu8wuix; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.results
    ADD CONSTRAINT fknp9gjedasyck4yaywivu8wuix FOREIGN KEY (execution_id) REFERENCES public.executions(id);


--
-- Name: acq_reversals fkoo9aie4nhgsgqc66jm5eibyer; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.acq_reversals
    ADD CONSTRAINT fkoo9aie4nhgsgqc66jm5eibyer FOREIGN KEY (acq_auth_id) REFERENCES public.acq_authorizations(id);


--
-- Name: acq_advices fkpanukx8mnlnqaqmhcngkrjrl9; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.acq_advices
    ADD CONSTRAINT fkpanukx8mnlnqaqmhcngkrjrl9 FOREIGN KEY (acq_auth_id) REFERENCES public.acq_authorizations(id);


--
-- Name: user_tests fkq87uw0q4uj3ufon78uw992i3i; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_tests
    ADD CONSTRAINT fkq87uw0q4uj3ufon78uw992i3i FOREIGN KEY (test_id) REFERENCES public.tests(id);


--
-- Name: executions fkqsfto9gjlbm9s215jgyan4gmh; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.executions
    ADD CONSTRAINT fkqsfto9gjlbm9s215jgyan4gmh FOREIGN KEY (user_id) REFERENCES public.users(id);


--
-- Name: campaigns fkyfby4s4hyhrlmj6j3c63xt6; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.campaigns
    ADD CONSTRAINT fkyfby4s4hyhrlmj6j3c63xt6 FOREIGN KEY (created_by) REFERENCES public.users(id);


--
-- PostgreSQL database dump complete
--

\unrestrict 3HVePXo8T9CT2ojdaoTGBOkjT4EOgRbQ7WM4tpO3aqmDyWLqmH2mmrO2dTHzZA4

-- ============================================================
-- Repartition de la propriete des tables
-- (execute par postgres : d'abord tout a scenario_user, puis les exceptions)
-- ============================================================
-- Toutes les tables et sequences a scenario_user par defaut
DO $$ DECLARE r RECORD; BEGIN
  FOR r IN SELECT tablename FROM pg_tables WHERE schemaname='public' LOOP
    EXECUTE 'ALTER TABLE public.' || quote_ident(r.tablename) || ' OWNER TO scenario_user';
  END LOOP;
  FOR r IN SELECT sequencename FROM pg_sequences WHERE schemaname='public' LOOP
    EXECUTE 'ALTER SEQUENCE public.' || quote_ident(r.sequencename) || ' OWNER TO scenario_user';
  END LOOP;
END $$;

-- Exceptions : tables de l'acquereur
ALTER TABLE public.dmas_acq_keys OWNER TO dmas_acquirer_user;
ALTER TABLE public.dmas_kek OWNER TO dmas_acquirer_user;

-- Exceptions : tables de l'issuer
ALTER TABLE public.dmas_cards OWNER TO dmas_issuer_user;
ALTER TABLE public.dmas_iss_keys OWNER TO dmas_issuer_user;
ALTER TABLE public.dmas_transactions OWNER TO dmas_issuer_user;
ALTER TABLE public.key_store OWNER TO dmas_issuer_user;

-- Droits croises sur les tables partagees
GRANT SELECT, INSERT, UPDATE, DELETE ON public.users TO dmas_acquirer_user, dmas_issuer_user;
GRANT SELECT, INSERT, UPDATE, DELETE ON public.dmas_kek TO dmas_issuer_user;

-- Droits sur toutes les sequences pour les 3 users
DO $$ DECLARE r RECORD; BEGIN
  FOR r IN SELECT sequencename FROM pg_sequences WHERE schemaname='public' LOOP
    EXECUTE 'GRANT USAGE, SELECT, UPDATE ON SEQUENCE public.' || quote_ident(r.sequencename) || ' TO scenario_user, dmas_acquirer_user, dmas_issuer_user';
  END LOOP;
END $$;
