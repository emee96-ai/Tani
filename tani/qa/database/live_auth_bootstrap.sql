-- Minimal Auth/Storage API boundaries for the full public production schema.
-- This file is used only in fresh, isolated regression databases.
CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE EXTENSION IF NOT EXISTS pg_trgm;
DO $$ BEGIN
  IF NOT EXISTS(SELECT 1 FROM pg_roles WHERE rolname='anon') THEN CREATE ROLE anon NOLOGIN; END IF;
  IF NOT EXISTS(SELECT 1 FROM pg_roles WHERE rolname='authenticated') THEN CREATE ROLE authenticated NOLOGIN; END IF;
  IF NOT EXISTS(SELECT 1 FROM pg_roles WHERE rolname='service_role') THEN CREATE ROLE service_role NOLOGIN BYPASSRLS; END IF;
END $$;
CREATE SCHEMA IF NOT EXISTS auth;
CREATE SCHEMA IF NOT EXISTS private;
CREATE SCHEMA IF NOT EXISTS storage;
CREATE SCHEMA IF NOT EXISTS tests;
GRANT USAGE ON SCHEMA public,auth,tests TO anon,authenticated,service_role;
CREATE TABLE auth.users (
  id uuid PRIMARY KEY,
  email text,
  phone text,
  phone_confirmed_at timestamptz,
  raw_user_meta_data jsonb NOT NULL DEFAULT '{}'::jsonb,
  raw_app_meta_data jsonb NOT NULL DEFAULT '{}'::jsonb,
  banned_until timestamptz,
  deleted_at timestamptz
);
CREATE TABLE storage.buckets (id text PRIMARY KEY, name text NOT NULL, public boolean NOT NULL DEFAULT false);
CREATE TABLE storage.objects (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(), bucket_id text NOT NULL, name text NOT NULL,
  owner uuid, owner_id text, metadata jsonb, created_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE(bucket_id,name)
);
CREATE OR REPLACE FUNCTION auth.uid() RETURNS uuid LANGUAGE sql STABLE AS $$
 SELECT coalesce(nullif(current_setting('request.jwt.claim.sub',true),''),
   nullif(current_setting('request.jwt.claims',true),'')::jsonb->>'sub')::uuid
$$;
CREATE OR REPLACE FUNCTION auth.jwt() RETURNS jsonb LANGUAGE sql STABLE AS $$
 SELECT coalesce(nullif(current_setting('request.jwt.claims',true),'')::jsonb,'{}'::jsonb)
$$;
CREATE OR REPLACE FUNCTION auth.role() RETURNS text LANGUAGE sql STABLE AS $$
 SELECT coalesce(auth.jwt()->>'role',nullif(current_setting('request.jwt.claim.role',true),''))
$$;
GRANT EXECUTE ON FUNCTION auth.uid(),auth.jwt(),auth.role() TO anon,authenticated,service_role;
CREATE FUNCTION tests.assert_true(p_condition boolean,p_message text) RETURNS void LANGUAGE plpgsql AS $$
BEGIN
 IF p_condition IS DISTINCT FROM true THEN RAISE EXCEPTION 'assertion failed: %',p_message; END IF;
 RAISE NOTICE 'PASS: %',p_message;
END $$;
CREATE FUNCTION tests.expect_denied(p_statement text,p_message text) RETURNS void LANGUAGE plpgsql AS $$
BEGIN
 BEGIN EXECUTE p_statement;
 EXCEPTION WHEN insufficient_privilege THEN RAISE NOTICE 'PASS: %',p_message; RETURN; END;
 RAISE EXCEPTION 'Authorization unexpectedly allowed: %',p_message;
END $$;
GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA tests TO anon,authenticated,service_role;
