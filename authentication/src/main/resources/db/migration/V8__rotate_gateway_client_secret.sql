-- Rotate the compromised 'gateway' OAuth client secret. The original plaintext ('secret')
-- and its BCrypt hash were both committed to git, so the pair is burned. V2's seed is
-- immutable (never edit a committed migration), so the stored hash is superseded here.
--
-- Neither the plaintext nor its hash lives in the repo: the hash arrives via the Flyway
-- placeholder below, mapped from the GATEWAY_CLIENT_SECRET_HASH env var in
-- application-dev.yml (a BCrypt(12) hash, bare format matching the V2 seed and
-- PasswordConfiguration's BCryptPasswordEncoder(12)). The matching plaintext is served to
-- clients as GATEWAY_CLIENT_SECRET / OAUTH2_INTROSPECTION_CLIENT_SECRET. Tests supply a
-- dummy placeholder value in application-test.yml.

UPDATE clients
SET client_secret = '${gateway_client_secret_hash}',
    updated_at    = now()
WHERE client_id_name = 'gateway'
  AND deleted_at IS NULL;
