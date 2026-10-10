-- PostgreSQL: creates category tables and seeds defaults for every existing usuario.
-- Safe to re-run after new users are registered; existing customized rows are preserved.
BEGIN;

CREATE TABLE IF NOT EXISTS categoria (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    created_at TIMESTAMP,
    created_by VARCHAR(120),
    updated_at TIMESTAMP,
    updated_by VARCHAR(120),
    nome VARCHAR(100) NOT NULL,
    tipo VARCHAR(20) NOT NULL CHECK (tipo IN ('DESPESA', 'RECEITA')),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    tenant_id UUID NOT NULL,
    CONSTRAINT uk_categoria_tenant_tipo_nome UNIQUE (tenant_id, tipo, nome)
);

CREATE INDEX IF NOT EXISTS idx_categoria_tenant_id ON categoria (tenant_id);
CREATE UNIQUE INDEX IF NOT EXISTS uk_categoria_tenant_tipo_nome_ci
    ON categoria (tenant_id, tipo, lower(btrim(nome)));

CREATE TABLE IF NOT EXISTS subcategoria (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    created_at TIMESTAMP,
    created_by VARCHAR(120),
    updated_at TIMESTAMP,
    updated_by VARCHAR(120),
    nome VARCHAR(100) NOT NULL,
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    tipo VARCHAR(20) NOT NULL CONSTRAINT ck_subcategoria_tipo CHECK (tipo IN ('DESPESA', 'RECEITA')),
    padrao BOOLEAN NOT NULL DEFAULT FALSE,
    tenant_id UUID NOT NULL,
    categoria_id UUID NOT NULL,
    CONSTRAINT uk_subcategoria_categoria_nome UNIQUE (categoria_id, nome),
    CONSTRAINT fk_subcategoria_categoria FOREIGN KEY (categoria_id)
        REFERENCES categoria (id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_subcategoria_tenant_id ON subcategoria (tenant_id);
CREATE INDEX IF NOT EXISTS idx_subcategoria_categoria_id ON subcategoria (categoria_id);
CREATE UNIQUE INDEX IF NOT EXISTS uk_subcategoria_categoria_nome_ci
    ON subcategoria (categoria_id, lower(btrim(nome)));

ALTER TABLE subcategoria ADD COLUMN IF NOT EXISTS tipo VARCHAR(20);
ALTER TABLE subcategoria ADD COLUMN IF NOT EXISTS padrao BOOLEAN NOT NULL DEFAULT FALSE;
UPDATE subcategoria s
SET tipo = c.tipo
FROM categoria c
WHERE s.categoria_id = c.id AND s.tipo IS DISTINCT FROM c.tipo;
ALTER TABLE subcategoria ALTER COLUMN tipo SET NOT NULL;
ALTER TABLE subcategoria DROP CONSTRAINT IF EXISTS ck_subcategoria_tipo;
ALTER TABLE subcategoria ADD CONSTRAINT ck_subcategoria_tipo
    CHECK (tipo IN ('DESPESA', 'RECEITA'));
CREATE UNIQUE INDEX IF NOT EXISTS uk_subcategoria_tenant_tipo_padrao
    ON subcategoria (tenant_id, tipo)
    WHERE padrao = TRUE;

CREATE TEMPORARY TABLE categoria_seed_template (
    tipo VARCHAR(20) NOT NULL,
    categoria VARCHAR(100) NOT NULL,
    subcategoria VARCHAR(100) NOT NULL
) ON COMMIT DROP;

INSERT INTO categoria_seed_template (tipo, categoria, subcategoria) VALUES
    ('DESPESA', 'Moradia', 'Aluguel e financiamento'),
    ('DESPESA', 'Moradia', 'Condomínio'),
    ('DESPESA', 'Moradia', 'Manutenção e reparos'),
    ('DESPESA', 'Moradia', 'Móveis e decoração'),
    ('DESPESA', 'Alimentação', 'Supermercado'),
    ('DESPESA', 'Alimentação', 'Restaurantes e delivery'),
    ('DESPESA', 'Alimentação', 'Lanches e cafés'),
    ('DESPESA', 'Transporte', 'Combustível'),
    ('DESPESA', 'Transporte', 'Transporte público'),
    ('DESPESA', 'Transporte', 'Aplicativos e táxi'),
    ('DESPESA', 'Transporte', 'Manutenção e estacionamento'),
    ('DESPESA', 'Saúde', 'Plano de saúde'),
    ('DESPESA', 'Saúde', 'Consultas e exames'),
    ('DESPESA', 'Saúde', 'Medicamentos'),
    ('DESPESA', 'Educação', 'Mensalidades e cursos'),
    ('DESPESA', 'Educação', 'Livros e materiais'),
    ('DESPESA', 'Lazer', 'Passeios e eventos'),
    ('DESPESA', 'Lazer', 'Assinaturas e streaming'),
    ('DESPESA', 'Lazer', 'Esportes e hobbies'),
    ('DESPESA', 'Compras', 'Vestuário e acessórios'),
    ('DESPESA', 'Compras', 'Eletrônicos'),
    ('DESPESA', 'Contas e serviços', 'Água e esgoto'),
    ('DESPESA', 'Contas e serviços', 'Energia elétrica'),
    ('DESPESA', 'Contas e serviços', 'Internet e telefone'),
    ('DESPESA', 'Contas e serviços', 'Gás'),
    ('DESPESA', 'Impostos e taxas', 'Impostos'),
    ('DESPESA', 'Impostos e taxas', 'Tarifas bancárias'),
    ('DESPESA', 'Viagens', 'Hospedagem'),
    ('DESPESA', 'Viagens', 'Passagens'),
    ('DESPESA', 'Viagens', 'Alimentação e passeios'),
    ('DESPESA', 'Outros', 'Despesas diversas'),
    ('RECEITA', 'Salário', 'Salário mensal'),
    ('RECEITA', 'Salário', 'Décimo terceiro'),
    ('RECEITA', 'Salário', 'Férias e bônus'),
    ('RECEITA', 'Trabalho e serviços', 'Freelance'),
    ('RECEITA', 'Trabalho e serviços', 'Serviços prestados'),
    ('RECEITA', 'Vendas', 'Produtos'),
    ('RECEITA', 'Vendas', 'Bens usados'),
    ('RECEITA', 'Investimentos', 'Rendimentos'),
    ('RECEITA', 'Investimentos', 'Dividendos e juros'),
    ('RECEITA', 'Benefícios', 'Benefícios e auxílios'),
    ('RECEITA', 'Reembolsos', 'Reembolso de despesas'),
    ('RECEITA', 'Aluguéis', 'Aluguel recebido'),
    ('RECEITA', 'Outros', 'Receitas diversas');

INSERT INTO categoria (
    id, created_at, created_by, updated_at, updated_by, tenant_id, nome, tipo, ativo
)
SELECT
    gen_random_uuid(), CURRENT_TIMESTAMP, 'seed-categorias', NULL, NULL,
    usuario.id, modelos.categoria, modelos.tipo, TRUE
FROM usuario
CROSS JOIN (
    SELECT DISTINCT tipo, categoria
    FROM categoria_seed_template
) modelos
ON CONFLICT DO NOTHING;

INSERT INTO subcategoria (
    id, created_at, created_by, updated_at, updated_by,
    tenant_id, categoria_id, nome, ativo, tipo, padrao
)
SELECT
    gen_random_uuid(), CURRENT_TIMESTAMP, 'seed-categorias', NULL, NULL,
    usuario.id, categoria.id, modelos.subcategoria, TRUE, categoria.tipo, FALSE
FROM usuario
JOIN categoria ON categoria.tenant_id = usuario.id
JOIN categoria_seed_template modelos
    ON modelos.tipo = categoria.tipo
    AND modelos.categoria = categoria.nome
ON CONFLICT DO NOTHING;

COMMIT;
