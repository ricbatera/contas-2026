-- Entradas (receitas). A avulsa tem uma ocorrência; a recorrente gera uma ocorrência prevista por mês.
CREATE TABLE entrada (
    id uuid NOT NULL,
    created_at timestamp(6) without time zone,
    created_by character varying(120),
    updated_at timestamp(6) without time zone,
    updated_by character varying(120),
    tenant_id uuid NOT NULL,
    descricao character varying(120) NOT NULL,
    valor numeric(12,2) NOT NULL,
    recorrente boolean DEFAULT false NOT NULL,
    data_inicial date NOT NULL,
    data_final date,
    categoria_id uuid NOT NULL,
    subcategoria_id uuid NOT NULL,
    conta_bancaria_id uuid,
    CONSTRAINT entrada_pkey PRIMARY KEY (id),
    CONSTRAINT fk_entrada_tenant FOREIGN KEY (tenant_id) REFERENCES usuario (id),
    CONSTRAINT fk_entrada_categoria FOREIGN KEY (categoria_id) REFERENCES categoria (id),
    CONSTRAINT fk_entrada_subcategoria FOREIGN KEY (subcategoria_id) REFERENCES subcategoria (id),
    CONSTRAINT fk_entrada_conta_bancaria FOREIGN KEY (conta_bancaria_id) REFERENCES conta_bancaria (id),
    CONSTRAINT ck_entrada_valor CHECK (valor > 0),
    CONSTRAINT ck_entrada_data_final CHECK (
        data_final IS NULL OR (recorrente AND data_final >= data_inicial))
);

CREATE INDEX idx_entrada_tenant ON entrada (tenant_id);
CREATE INDEX idx_entrada_categoria ON entrada (categoria_id);
CREATE INDEX idx_entrada_subcategoria ON entrada (subcategoria_id);
CREATE INDEX idx_entrada_conta_bancaria ON entrada (conta_bancaria_id);

-- Uma ocorrência por mês (competência = dia 1). Recebida exige valor e data reais.
CREATE TABLE entrada_ocorrencia (
    id uuid NOT NULL,
    created_at timestamp(6) without time zone,
    created_by character varying(120),
    updated_at timestamp(6) without time zone,
    updated_by character varying(120),
    tenant_id uuid NOT NULL,
    entrada_id uuid NOT NULL,
    competencia date NOT NULL,
    data_prevista date NOT NULL,
    valor_previsto numeric(12,2) NOT NULL,
    status character varying(20) NOT NULL,
    valor_recebido numeric(12,2),
    data_recebimento date,
    CONSTRAINT entrada_ocorrencia_pkey PRIMARY KEY (id),
    CONSTRAINT fk_entrada_ocorrencia_tenant FOREIGN KEY (tenant_id) REFERENCES usuario (id),
    CONSTRAINT fk_entrada_ocorrencia_entrada FOREIGN KEY (entrada_id) REFERENCES entrada (id) ON DELETE CASCADE,
    CONSTRAINT uk_entrada_ocorrencia_competencia UNIQUE (entrada_id, competencia),
    CONSTRAINT ck_entrada_ocorrencia_competencia CHECK (EXTRACT(DAY FROM competencia) = 1),
    CONSTRAINT ck_entrada_ocorrencia_valor_previsto CHECK (valor_previsto > 0),
    CONSTRAINT ck_entrada_ocorrencia_valor_recebido CHECK (valor_recebido IS NULL OR valor_recebido > 0),
    CONSTRAINT ck_entrada_ocorrencia_status CHECK (status IN ('PREVISTA', 'RECEBIDA', 'CANCELADA')),
    CONSTRAINT ck_entrada_ocorrencia_recebimento CHECK (
        (status = 'RECEBIDA' AND valor_recebido IS NOT NULL AND data_recebimento IS NOT NULL)
        OR (status <> 'RECEBIDA' AND valor_recebido IS NULL AND data_recebimento IS NULL))
);

CREATE INDEX idx_entrada_ocorrencia_tenant_competencia ON entrada_ocorrencia (tenant_id, competencia);
