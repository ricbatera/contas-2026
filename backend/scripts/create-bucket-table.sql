CREATE TABLE IF NOT EXISTS bucket_orcamento (
    id UUID PRIMARY KEY,
    created_at TIMESTAMP,
    created_by VARCHAR(120),
    updated_at TIMESTAMP,
    updated_by VARCHAR(120),
    subcategoria_id UUID NOT NULL REFERENCES subcategoria (id),
    usuario_id UUID REFERENCES usuario (id),
    pessoa_id UUID REFERENCES pessoa (id),
    valor_mensal NUMERIC(12, 2) NOT NULL CHECK (valor_mensal > 0),
    tenant_id UUID NOT NULL,
    CONSTRAINT ck_bucket_responsavel CHECK (
        (usuario_id IS NOT NULL AND pessoa_id IS NULL)
        OR (usuario_id IS NULL AND pessoa_id IS NOT NULL)
    )
);

CREATE INDEX IF NOT EXISTS idx_bucket_orcamento_tenant
    ON bucket_orcamento (tenant_id);

CREATE UNIQUE INDEX IF NOT EXISTS uk_bucket_orcamento_usuario_subcategoria
    ON bucket_orcamento (tenant_id, subcategoria_id, usuario_id)
    WHERE usuario_id IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_bucket_orcamento_pessoa_subcategoria
    ON bucket_orcamento (tenant_id, subcategoria_id, pessoa_id)
    WHERE pessoa_id IS NOT NULL;
