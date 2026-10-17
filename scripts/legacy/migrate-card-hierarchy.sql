\set ON_ERROR_STOP on

-- Add principal/additional relationships and an independent virtual-card flag.
-- Legacy VIRTUAL cards become ADICIONAL + virtual=true when they had a principal;
-- otherwise they become PRINCIPAL + virtual=true.
BEGIN;

LOCK TABLE public.cartao IN ACCESS EXCLUSIVE MODE;

ALTER TABLE public.cartao
    ADD COLUMN IF NOT EXISTS tipo varchar(20) NOT NULL DEFAULT 'PRINCIPAL',
    ADD COLUMN IF NOT EXISTS virtual boolean NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS cartao_principal_id uuid;

DO $$
BEGIN
    IF to_regclass('public.cartao_legacy_20261009') IS NOT NULL THEN
        IF EXISTS (
            SELECT 1 FROM public.cartao_legacy_20261009
            WHERE tipo NOT IN ('PRINCIPAL', 'ADICIONAL', 'VIRTUAL')
               OR (tipo = 'PRINCIPAL' AND cartao_principal_id IS NOT NULL)
               OR (tipo = 'ADICIONAL' AND cartao_principal_id IS NULL)
        ) THEN
            RAISE EXCEPTION 'Card hierarchy migration aborted: unsupported legacy card type';
        END IF;

        IF EXISTS (
            SELECT 1
            FROM public.cartao_legacy_20261009 AS legacy
            LEFT JOIN public.cartao AS current_card
                ON current_card.nome = legacy.nome
                AND current_card.banco = legacy.banco
                AND current_card.bandeira = legacy.bandeira
                AND current_card.ultimos_digitos = legacy.ultimos_digitos
                AND current_card.tenant_id = legacy.tenant_id
            GROUP BY legacy.id
            HAVING count(current_card.id) <> 1
        ) THEN
            RAISE EXCEPTION 'Card hierarchy migration aborted: legacy card mapping is missing or ambiguous';
        END IF;

        IF EXISTS (
            SELECT 1
            FROM public.cartao_legacy_20261009 AS legacy
            LEFT JOIN public.cartao_legacy_20261009 AS legacy_parent
                ON legacy_parent.id = legacy.cartao_principal_id
            LEFT JOIN public.cartao AS current_parent
                ON current_parent.nome = legacy_parent.nome
                AND current_parent.banco = legacy_parent.banco
                AND current_parent.bandeira = legacy_parent.bandeira
                AND current_parent.ultimos_digitos = legacy_parent.ultimos_digitos
                AND current_parent.tenant_id = legacy.tenant_id
            WHERE legacy.cartao_principal_id IS NOT NULL
                AND (
                    legacy_parent.tipo = 'ADICIONAL'
                    OR legacy_parent.cartao_principal_id IS NOT NULL
                    OR current_parent.id IS NULL
                )
        ) THEN
            RAISE EXCEPTION 'Card hierarchy migration aborted: legacy principal mapping is invalid';
        END IF;

        UPDATE public.cartao AS current_card
        SET tipo = CASE
                WHEN legacy.cartao_principal_id IS NULL THEN 'PRINCIPAL'
                ELSE 'ADICIONAL'
            END,
            virtual = legacy.tipo = 'VIRTUAL',
            cartao_principal_id = parent_current.id
        FROM public.cartao_legacy_20261009 AS legacy
        LEFT JOIN public.cartao_legacy_20261009 AS legacy_parent
            ON legacy_parent.id = legacy.cartao_principal_id
        LEFT JOIN public.cartao AS parent_current
            ON parent_current.nome = legacy_parent.nome
            AND parent_current.banco = legacy_parent.banco
            AND parent_current.bandeira = legacy_parent.bandeira
            AND parent_current.ultimos_digitos = legacy_parent.ultimos_digitos
            AND parent_current.tenant_id = legacy.tenant_id
        WHERE current_card.nome = legacy.nome
            AND current_card.banco = legacy.banco
            AND current_card.bandeira = legacy.bandeira
            AND current_card.ultimos_digitos = legacy.ultimos_digitos
            AND current_card.tenant_id = legacy.tenant_id;
    END IF;
END $$;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM public.cartao
        WHERE (tipo = 'PRINCIPAL' AND cartao_principal_id IS NOT NULL)
           OR (tipo = 'ADICIONAL' AND cartao_principal_id IS NULL)
    ) THEN
        RAISE EXCEPTION 'Card hierarchy migration aborted: inconsistent principal relationship';
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'public.cartao'::regclass AND conname = 'cartao_tipo_valido'
    ) THEN
        ALTER TABLE public.cartao
            ADD CONSTRAINT cartao_tipo_valido CHECK (tipo IN ('PRINCIPAL', 'ADICIONAL'));
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'public.cartao'::regclass AND conname = 'cartao_associacao_tipo_valida'
    ) THEN
        ALTER TABLE public.cartao
            ADD CONSTRAINT cartao_associacao_tipo_valida
            CHECK (
                (tipo = 'PRINCIPAL' AND cartao_principal_id IS NULL)
                OR (tipo = 'ADICIONAL' AND cartao_principal_id IS NOT NULL)
            );
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'public.cartao'::regclass AND conname = 'cartao_principal_fk'
    ) THEN
        ALTER TABLE public.cartao
            ADD CONSTRAINT cartao_principal_fk
            FOREIGN KEY (cartao_principal_id) REFERENCES public.cartao(id);
    END IF;
END $$;

COMMIT;
