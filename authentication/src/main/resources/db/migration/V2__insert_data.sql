insert into clients (auth_method, client_secret, client_id_name)
values ('client_secret_basic', '$2a$12$lmJlz3HawNWFHMhj2r2lo.iwj4fcPOtcxi..PXJWq.hfhybphafWG', 'gateway')
returning client_id;

insert into scopes (scope)
values ('catalog.read'),
       ('catalog.write'),
       ('profile.read'),
       ('profile.write'),
       ('order.read'),
       ('order.write'),
       ('notification.read')
returning scope_id;

insert into clients_scopes (scope_id, client_id)
select s.scope_id, c.client_id from scopes s, clients c;

-- access and refresh token are in seconds
insert into token_settings (access_token_time_to_live, refresh_token_time_to_live)
values (600, 7200)
returning token_setting_id;

insert into clients_token_settings (token_setting_id, client_id)
select t.token_setting_id, c.client_id from token_settings t, clients c;

insert into grant_types (grant_type)
values ('authorization_code'),
       ('refresh_token'),
       ('client_credentials')
returning grant_type_id;

insert into clients_grant_types (grant_type_id, client_id)
select g.grant_type_id, c.client_id from grant_types g, clients c;

insert into redirect_uris (client_id, redirect_uri)
values ((select client_id from clients where client_id_name = 'gateway'), 'http://127.0.0.1:8081/login/oauth2/code/gateway-client-oidc'),
       ((select client_id from clients where client_id_name = 'gateway'), 'http://127.0.0.1:8081/authorized');