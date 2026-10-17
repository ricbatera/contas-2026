CREATE TABLE IF NOT EXISTS pessoa (
    id UUID PRIMARY KEY,
    created_at TIMESTAMP,
    created_by VARCHAR(120),
    updated_at TIMESTAMP,
    updated_by VARCHAR(120),
    nome VARCHAR(100) NOT NULL,
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    tenant_id UUID NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_pessoa_tenant_id ON pessoa (tenant_id);
