-- PostgreSQL migration for expense installments, responsibility shares and credit-card invoices.
BEGIN;

ALTER TABLE cartao ADD COLUMN IF NOT EXISTS ativo BOOLEAN NOT NULL DEFAULT TRUE;

CREATE TABLE IF NOT EXISTS fatura (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    created_at TIMESTAMP,
    created_by VARCHAR(120),
    updated_at TIMESTAMP,
    updated_by VARCHAR(120),
    cartao_principal_id UUID NOT NULL REFERENCES cartao (id),
    competencia INTEGER NOT NULL,
    vencimento DATE NOT NULL,
    tenant_id UUID NOT NULL REFERENCES usuario (id),
    CONSTRAINT uk_fatura_cartao_competencia UNIQUE (tenant_id, cartao_principal_id, competencia)
);

CREATE TABLE IF NOT EXISTS saida (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    created_at TIMESTAMP,
    created_by VARCHAR(120),
    updated_at TIMESTAMP,
    updated_by VARCHAR(120),
    descricao VARCHAR(120) NOT NULL,
    descricao_fatura VARCHAR(120),
    valor_por_parcela NUMERIC(12, 2) NOT NULL CHECK (valor_por_parcela > 0),
    quantidade_parcelas INTEGER NOT NULL,
    forma_pagamento VARCHAR(20) NOT NULL
        CHECK (forma_pagamento IN ('CARTAO_CREDITO', 'BOLETO', 'DEBITO', 'PIX')),
    modo_responsabilidade VARCHAR(20) NOT NULL
        CHECK (modo_responsabilidade IN ('USUARIO', 'PESSOA', 'DIVIDIR')),
    data_inicial DATE,
    primeiro_vencimento DATE,
    cartao_id UUID REFERENCES cartao (id),
    categoria_id UUID NOT NULL REFERENCES categoria (id),
    subcategoria_id UUID NOT NULL REFERENCES subcategoria (id),
    tenant_id UUID NOT NULL REFERENCES usuario (id),
    CONSTRAINT ck_saida_dados_pagamento CHECK (
        (forma_pagamento = 'CARTAO_CREDITO'
            AND cartao_id IS NOT NULL
            AND primeiro_vencimento IS NOT NULL
            AND data_inicial IS NULL)
        OR
        (forma_pagamento <> 'CARTAO_CREDITO'
            AND cartao_id IS NULL
            AND primeiro_vencimento IS NULL
            AND data_inicial IS NOT NULL
            AND descricao_fatura IS NULL)
    )
);

CREATE TABLE IF NOT EXISTS saida_parcela (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    created_at TIMESTAMP,
    created_by VARCHAR(120),
    updated_at TIMESTAMP,
    updated_by VARCHAR(120),
    numero INTEGER NOT NULL,
    valor NUMERIC(12, 2) NOT NULL CHECK (valor > 0),
    vencimento DATE NOT NULL,
    saida_id UUID NOT NULL REFERENCES saida (id) ON DELETE CASCADE,
    fatura_id UUID REFERENCES fatura (id),
    tenant_id UUID NOT NULL REFERENCES usuario (id),
    CONSTRAINT uk_saida_parcela_numero UNIQUE (saida_id, numero)
);

ALTER TABLE saida DROP CONSTRAINT IF EXISTS saida_quantidade_parcelas_check;
ALTER TABLE saida DROP CONSTRAINT IF EXISTS ck_saida_quantidade_parcelas;
ALTER TABLE saida
    ADD CONSTRAINT ck_saida_quantidade_parcelas CHECK (quantidade_parcelas BETWEEN 1 AND 60);

ALTER TABLE saida_parcela DROP CONSTRAINT IF EXISTS saida_parcela_numero_check;
ALTER TABLE saida_parcela DROP CONSTRAINT IF EXISTS ck_saida_parcela_numero;
ALTER TABLE saida_parcela
    ADD CONSTRAINT ck_saida_parcela_numero CHECK (numero BETWEEN 1 AND 60);

CREATE TABLE IF NOT EXISTS saida_responsavel (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    created_at TIMESTAMP,
    created_by VARCHAR(120),
    updated_at TIMESTAMP,
    updated_by VARCHAR(120),
    valor_por_parcela NUMERIC(12, 2) NOT NULL CHECK (valor_por_parcela > 0),
    saida_id UUID NOT NULL REFERENCES saida (id) ON DELETE CASCADE,
    usuario_id UUID REFERENCES usuario (id),
    pessoa_id UUID REFERENCES pessoa (id),
    tenant_id UUID NOT NULL REFERENCES usuario (id),
    CONSTRAINT ck_saida_responsavel_proprietario CHECK (
        (usuario_id IS NOT NULL AND pessoa_id IS NULL)
        OR (usuario_id IS NULL AND pessoa_id IS NOT NULL)
    ),
    CONSTRAINT uk_saida_responsavel_usuario UNIQUE (saida_id, usuario_id)
);

CREATE INDEX IF NOT EXISTS idx_fatura_tenant_id ON fatura (tenant_id);
CREATE INDEX IF NOT EXISTS idx_fatura_cartao_competencia ON fatura (cartao_principal_id, competencia);
CREATE INDEX IF NOT EXISTS idx_saida_tenant_id ON saida (tenant_id);
CREATE INDEX IF NOT EXISTS idx_saida_cartao_id ON saida (cartao_id);
CREATE INDEX IF NOT EXISTS idx_saida_categoria_id ON saida (categoria_id);
CREATE INDEX IF NOT EXISTS idx_saida_subcategoria_id ON saida (subcategoria_id);
CREATE INDEX IF NOT EXISTS idx_saida_parcela_tenant_id ON saida_parcela (tenant_id);
CREATE INDEX IF NOT EXISTS idx_saida_parcela_vencimento ON saida_parcela (vencimento);
CREATE INDEX IF NOT EXISTS idx_saida_parcela_fatura_id ON saida_parcela (fatura_id);
CREATE INDEX IF NOT EXISTS idx_saida_responsavel_tenant_id ON saida_responsavel (tenant_id);
CREATE INDEX IF NOT EXISTS idx_saida_responsavel_pessoa_id ON saida_responsavel (pessoa_id);

COMMIT;
