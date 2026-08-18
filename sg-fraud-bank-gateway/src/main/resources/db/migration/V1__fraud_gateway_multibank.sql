create table fraud_gateway_member (
    member_id varchar(64) primary key,
    display_name varchar(160) not null,
    active boolean not null,
    created_at timestamp with time zone not null
);

create table fraud_gateway_member_sector (
    id uuid primary key,
    member_id varchar(64) not null,
    sector_id varchar(64) not null,
    display_name varchar(160) not null,
    active boolean not null,
    created_at timestamp with time zone not null,
    constraint fk_gateway_sector_member foreign key (member_id) references fraud_gateway_member(member_id),
    constraint uk_gateway_member_sector unique (member_id, sector_id)
);

create table fraud_gateway_connection_profile (
    id uuid primary key,
    connection_code varchar(96) not null,
    member_id varchar(64) not null,
    sector_id varchar(64),
    protocol varchar(16) not null,
    connection_mode varchar(16) not null,
    listen_port integer not null,
    remote_host varchar(253),
    remote_port integer,
    message_profile varchar(128),
    credential_reference varchar(160),
    zmk_reference varchar(160),
    echo_interval_seconds integer not null,
    reconnect_backoff_seconds integer not null,
    active boolean not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint fk_gateway_profile_member foreign key (member_id) references fraud_gateway_member(member_id),
    constraint fk_gateway_profile_sector foreign key (member_id, sector_id)
        references fraud_gateway_member_sector(member_id, sector_id),
    constraint ck_gateway_protocol check (protocol in ('ISO8583', 'REST')),
    constraint ck_gateway_connection_mode check (connection_mode in ('SERVER', 'CLIENT')),
    constraint ck_gateway_listen_port check (listen_port between 1024 and 65535),
    constraint ck_gateway_remote_port check (remote_port is null or remote_port between 1 and 65535),
    constraint ck_gateway_echo check (echo_interval_seconds between 1 and 3600),
    constraint ck_gateway_backoff check (reconnect_backoff_seconds between 1 and 3600),
    constraint uk_gateway_connection_code unique (connection_code),
    constraint uk_gateway_protocol_listen_port unique (protocol, listen_port)
);

create index ix_gateway_connection_member_sector
    on fraud_gateway_connection_profile(member_id, sector_id);
create index ix_gateway_connection_active
    on fraud_gateway_connection_profile(active, protocol);
