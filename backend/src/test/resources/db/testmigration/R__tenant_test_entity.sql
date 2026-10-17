-- Tabela só de teste para TenantTestEntity (TenantFilterIT).
CREATE TABLE IF NOT EXISTS tenant_test_entity (
    id uuid NOT NULL PRIMARY KEY,
    created_at timestamp(6) without time zone,
    created_by character varying(120),
    updated_at timestamp(6) without time zone,
    updated_by character varying(120),
    tenant_id uuid NOT NULL,
    descricao character varying(255) NOT NULL
);
