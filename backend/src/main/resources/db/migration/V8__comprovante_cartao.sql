-- Dias de fechamento e vencimento da fatura, só no cartão principal (o adicional usa os do principal).
ALTER TABLE cartao ADD COLUMN dia_fechamento integer;
ALTER TABLE cartao ADD COLUMN dia_vencimento integer;
ALTER TABLE cartao ADD CONSTRAINT ck_cartao_dia_fechamento CHECK (dia_fechamento BETWEEN 1 AND 31);
ALTER TABLE cartao ADD CONSTRAINT ck_cartao_dia_vencimento CHECK (dia_vencimento BETWEEN 1 AND 31);

-- Fotos de comprovantes de maquininha lidas por IA; viram saída após aprovação do usuário.
CREATE TABLE comprovante_cartao (
    id uuid NOT NULL,
    created_at timestamp(6) without time zone,
    created_by character varying(120),
    updated_at timestamp(6) without time zone,
    updated_by character varying(120),
    tenant_id uuid NOT NULL,
    nome_arquivo character varying(255) NOT NULL,
    imagem bytea NOT NULL,
    tipo_midia character varying(30) NOT NULL,
    tamanho_bytes integer NOT NULL,
    hash_sha256 character varying(64) NOT NULL,
    status character varying(20) NOT NULL,
    erro_leitura text,
    avisos text,
    estabelecimento character varying(120),
    ultimos_digitos_lidos character varying(4),
    bandeira_lida character varying(20),
    resposta_ia text,
    provedor_modelo character varying(100),
    modalidade character varying(20) NOT NULL,
    descricao character varying(120),
    data_compra date,
    valor_total numeric(12,2),
    quantidade_parcelas integer NOT NULL,
    primeiro_vencimento date,
    cartao_id uuid,
    categoria_id uuid,
    subcategoria_id uuid,
    saida_id uuid,
    CONSTRAINT comprovante_cartao_pkey PRIMARY KEY (id),
    CONSTRAINT fk_comprovante_cartao_tenant FOREIGN KEY (tenant_id) REFERENCES usuario (id),
    CONSTRAINT fk_comprovante_cartao_cartao FOREIGN KEY (cartao_id) REFERENCES cartao (id) ON DELETE SET NULL,
    CONSTRAINT fk_comprovante_cartao_categoria FOREIGN KEY (categoria_id) REFERENCES categoria (id) ON DELETE SET NULL,
    CONSTRAINT fk_comprovante_cartao_subcategoria FOREIGN KEY (subcategoria_id) REFERENCES subcategoria (id) ON DELETE SET NULL,
    CONSTRAINT fk_comprovante_cartao_saida FOREIGN KEY (saida_id) REFERENCES saida (id) ON DELETE SET NULL,
    CONSTRAINT ck_comprovante_cartao_status CHECK (status IN ('PENDENTE', 'APROVADO', 'REJEITADO')),
    CONSTRAINT ck_comprovante_cartao_modalidade CHECK (modalidade IN ('CREDITO', 'DEBITO')),
    CONSTRAINT ck_comprovante_cartao_valor CHECK (valor_total IS NULL OR valor_total > 0),
    CONSTRAINT ck_comprovante_cartao_parcelas CHECK (quantidade_parcelas BETWEEN 1 AND 60),
    CONSTRAINT ck_comprovante_cartao_tamanho CHECK (tamanho_bytes > 0)
);

CREATE INDEX idx_comprovante_cartao_tenant_status ON comprovante_cartao (tenant_id, status);
CREATE INDEX idx_comprovante_cartao_hash ON comprovante_cartao (tenant_id, hash_sha256);
