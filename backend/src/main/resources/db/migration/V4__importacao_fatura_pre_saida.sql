-- Importação de faturas de cartão em PDF lidas por IA. O PDF é gravado já sem senha.
CREATE TABLE fatura_importacao (
    id uuid NOT NULL,
    created_at timestamp(6) without time zone,
    created_by character varying(120),
    updated_at timestamp(6) without time zone,
    updated_by character varying(120),
    tenant_id uuid NOT NULL,
    nome_arquivo character varying(255) NOT NULL,
    pdf bytea NOT NULL,
    hash_sha256 character varying(64) NOT NULL,
    tamanho_bytes integer NOT NULL,
    cartao_principal_id uuid,
    competencia integer,
    vencimento date,
    total_pdf numeric(12,2),
    status character varying(20) NOT NULL,
    erro text,
    resposta_ia text,
    resumo text,
    provedor_modelo character varying(100),
    CONSTRAINT fatura_importacao_pkey PRIMARY KEY (id),
    CONSTRAINT fk_fatura_importacao_tenant FOREIGN KEY (tenant_id) REFERENCES usuario (id),
    CONSTRAINT fk_fatura_importacao_cartao FOREIGN KEY (cartao_principal_id) REFERENCES cartao (id) ON DELETE SET NULL,
    CONSTRAINT ck_fatura_importacao_status CHECK (status IN ('PROCESSANDO', 'CONCLUIDA', 'ERRO')),
    CONSTRAINT ck_fatura_importacao_tamanho CHECK (tamanho_bytes > 0)
);

CREATE INDEX idx_fatura_importacao_tenant ON fatura_importacao (tenant_id);
CREATE INDEX idx_fatura_importacao_hash ON fatura_importacao (tenant_id, hash_sha256);

-- Compras lidas da fatura que ainda não existem no sistema; viram saída após aprovação.
CREATE TABLE pre_saida (
    id uuid NOT NULL,
    created_at timestamp(6) without time zone,
    created_by character varying(120),
    updated_at timestamp(6) without time zone,
    updated_by character varying(120),
    tenant_id uuid NOT NULL,
    importacao_id uuid NOT NULL,
    cartao_id uuid,
    descricao character varying(120) NOT NULL,
    descricao_fatura character varying(120),
    data_compra date,
    valor_por_parcela numeric(12,2) NOT NULL,
    numero_parcela integer NOT NULL,
    quantidade_parcelas integer NOT NULL,
    competencia integer NOT NULL,
    primeiro_vencimento date NOT NULL,
    categoria_id uuid,
    subcategoria_id uuid,
    status character varying(20) NOT NULL,
    saida_id uuid,
    CONSTRAINT pre_saida_pkey PRIMARY KEY (id),
    CONSTRAINT fk_pre_saida_tenant FOREIGN KEY (tenant_id) REFERENCES usuario (id),
    CONSTRAINT fk_pre_saida_importacao FOREIGN KEY (importacao_id) REFERENCES fatura_importacao (id) ON DELETE CASCADE,
    CONSTRAINT fk_pre_saida_cartao FOREIGN KEY (cartao_id) REFERENCES cartao (id) ON DELETE SET NULL,
    CONSTRAINT fk_pre_saida_categoria FOREIGN KEY (categoria_id) REFERENCES categoria (id) ON DELETE SET NULL,
    CONSTRAINT fk_pre_saida_subcategoria FOREIGN KEY (subcategoria_id) REFERENCES subcategoria (id) ON DELETE SET NULL,
    CONSTRAINT fk_pre_saida_saida FOREIGN KEY (saida_id) REFERENCES saida (id) ON DELETE SET NULL,
    CONSTRAINT ck_pre_saida_valor CHECK (valor_por_parcela > 0),
    CONSTRAINT ck_pre_saida_quantidade_parcelas CHECK (quantidade_parcelas BETWEEN 1 AND 60),
    CONSTRAINT ck_pre_saida_numero_parcela CHECK (numero_parcela BETWEEN 1 AND quantidade_parcelas),
    CONSTRAINT ck_pre_saida_status CHECK (status IN ('PENDENTE', 'APROVADA', 'REJEITADA'))
);

CREATE INDEX idx_pre_saida_tenant_status ON pre_saida (tenant_id, status);
CREATE INDEX idx_pre_saida_importacao ON pre_saida (importacao_id);
