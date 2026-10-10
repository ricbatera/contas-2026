\set ON_ERROR_STOP on

-- One-time migration for the legacy BIGINT cartao schema.
-- Run with: psql "$DB_URL" -v owner_uuid="<UUID>" -f backend/scripts/migrate-legacy-cartao-schema.sql
BEGIN;

CREATE TEMP TABLE cartao_migration_owner ON COMMIT DROP AS
SELECT id
FROM public.usuario
WHERE id = :'owner_uuid'::uuid;

DO $$
BEGIN
    IF (SELECT count(*) FROM cartao_migration_owner) <> 1 THEN
        RAISE EXCEPTION 'Cartao migration aborted: owner UUID does not identify exactly one user';
    END IF;
    IF to_regclass('public.cartao_legacy_20261009') IS NOT NULL THEN
        RAISE EXCEPTION 'Cartao migration aborted: archive table already exists';
    END IF;
END $$;

LOCK TABLE public.cartao IN ACCESS EXCLUSIVE MODE;

ALTER TABLE public.cartao
    ADD COLUMN IF NOT EXISTS tenant_id uuid;

UPDATE public.cartao
SET tenant_id = (SELECT id FROM cartao_migration_owner);

CREATE TEMP TABLE cartao_migration_ids ON COMMIT DROP AS
SELECT id AS legacy_id, gen_random_uuid() AS new_id
FROM public.cartao;

ALTER TABLE public.cartao RENAME TO cartao_legacy_20261009;

CREATE TABLE public.cartao (
    id uuid NOT NULL,
    created_at timestamp(6),
    created_by varchar(120),
    updated_at timestamp(6),
    updated_by varchar(120),
    banco varchar(100) NOT NULL,
    bandeira varchar(20) NOT NULL,
    cor varchar(7),
    nome varchar(50) NOT NULL,
    tenant_id uuid NOT NULL,
    ultimos_digitos varchar(4) NOT NULL,
    CONSTRAINT cartao_current_pkey PRIMARY KEY (id)
);

INSERT INTO public.cartao (
    id, created_at, created_by, updated_at, updated_by,
    banco, bandeira, cor, nome, tenant_id, ultimos_digitos
)
SELECT
    ids.new_id, old.created_at, old.created_by, old.updated_at, old.updated_by,
    old.banco, old.bandeira, old.cor, old.nome, old.tenant_id, old.ultimos_digitos
FROM public.cartao_legacy_20261009 AS old
JOIN cartao_migration_ids AS ids ON ids.legacy_id = old.id;

DO $$
BEGIN
    IF (SELECT count(*) FROM public.cartao) <>
       (SELECT count(*) FROM public.cartao_legacy_20261009) THEN
        RAISE EXCEPTION 'Cartao migration aborted: not all legacy records were copied';
    END IF;
END $$;

COMMIT;
