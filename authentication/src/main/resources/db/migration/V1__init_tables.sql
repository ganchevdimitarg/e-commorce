create table clients
(
    client_id      uuid DEFAULT gen_random_uuid() not null,
    auth_method    varchar(255)                   not null,
    client_secret  varchar(255)                   not null,
    client_id_name varchar(255)                   not null,
    primary key (client_id)
);

create table grant_types
(
    grant_type_id uuid DEFAULT gen_random_uuid() not null,
    grant_type    varchar(255)                   not null,
    primary key (grant_type_id)
);

create table redirect_uris
(
    redirect_uri_id uuid DEFAULT gen_random_uuid() not null,
    redirect_uri    varchar(255)                   not null,
    client_id       uuid DEFAULT gen_random_uuid() not null,
    primary key (redirect_uri_id),
    CONSTRAINT fk_redirect_uris FOREIGN KEY (client_id) REFERENCES clients (client_id)
);

create table scopes
(
    scope_id uuid DEFAULT gen_random_uuid() not null,
    scope    varchar(255)                   not null,
    primary key (scope_id)
);

create table token_settings
(
    token_setting_id           uuid DEFAULT gen_random_uuid() not null,
    access_token_time_to_live  bigint                         not null,
    refresh_token_time_to_live bigint                         not null,
    primary key (token_setting_id)
);

create table clients_grant_types
(
    grant_type_id uuid DEFAULT gen_random_uuid() not null,
    client_id     uuid DEFAULT gen_random_uuid() not null,
    primary key (grant_type_id, client_id),
    CONSTRAINT fk_grant_types FOREIGN KEY (grant_type_id) REFERENCES grant_types (grant_type_id),
    CONSTRAINT fk_clients FOREIGN KEY (client_id) REFERENCES clients (client_id)

);

create table clients_scopes
(
    scope_id  uuid DEFAULT gen_random_uuid() not null,
    client_id uuid DEFAULT gen_random_uuid() not null,
    primary key (scope_id, client_id),
    CONSTRAINT fk_scopes FOREIGN KEY (scope_id) REFERENCES scopes (scope_id),
    CONSTRAINT fk_clients FOREIGN KEY (client_id) REFERENCES clients (client_id)
);

create table clients_token_settings
(
    token_setting_id uuid DEFAULT gen_random_uuid() not null,
    client_id        uuid DEFAULT gen_random_uuid() not null,
    primary key (token_setting_id, client_id),
    CONSTRAINT fk_token_settings FOREIGN KEY (token_setting_id) REFERENCES token_settings (token_setting_id),
    CONSTRAINT fk_clients FOREIGN KEY (client_id) REFERENCES clients (client_id)
);
