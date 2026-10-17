-- Aplica regras definidas nos scripts SQL anteriores ao Flyway (scripts/legacy/) que não
-- chegaram ao banco, porque as tabelas foram criadas pelo Hibernate (ddl-auto=update).

-- saida
ALTER TABLE saida
    ADD CONSTRAINT ck_saida_valor_por_parcela CHECK (valor_por_parcela > 0),
    ADD CONSTRAINT ck_saida_quantidade_parcelas CHECK (quantidade_parcelas BETWEEN 1 AND 60),
    ADD CONSTRAINT ck_saida_dados_pagamento CHECK (
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
    ),
    ADD CONSTRAINT fk_saida_tenant FOREIGN KEY (tenant_id) REFERENCES usuario (id);

-- saida_parcela
ALTER TABLE saida_parcela
    ADD CONSTRAINT ck_saida_parcela_valor CHECK (valor > 0),
    ADD CONSTRAINT ck_saida_parcela_numero CHECK (numero BETWEEN 1 AND 60),
    ADD CONSTRAINT fk_saida_parcela_tenant FOREIGN KEY (tenant_id) REFERENCES usuario (id);

ALTER TABLE saida_parcela DROP CONSTRAINT fkb7mllf3gec1susioebxxou7uw;
ALTER TABLE saida_parcela
    ADD CONSTRAINT fk_saida_parcela_saida FOREIGN KEY (saida_id) REFERENCES saida (id) ON DELETE CASCADE;

-- saida_responsavel
ALTER TABLE saida_responsavel
    ADD CONSTRAINT ck_saida_responsavel_valor CHECK (valor_por_parcela > 0),
    ADD CONSTRAINT fk_saida_responsavel_tenant FOREIGN KEY (tenant_id) REFERENCES usuario (id);

ALTER TABLE saida_responsavel DROP CONSTRAINT fkmm6v1xyhk9mx95tygcrsb2i50;
ALTER TABLE saida_responsavel
    ADD CONSTRAINT fk_saida_responsavel_saida FOREIGN KEY (saida_id) REFERENCES saida (id) ON DELETE CASCADE;

-- fatura
ALTER TABLE fatura
    ADD CONSTRAINT fk_fatura_tenant FOREIGN KEY (tenant_id) REFERENCES usuario (id);

-- bucket_orcamento
ALTER TABLE bucket_orcamento
    ADD CONSTRAINT ck_bucket_valor_mensal CHECK (valor_mensal > 0);

CREATE INDEX idx_bucket_orcamento_tenant ON bucket_orcamento (tenant_id);

CREATE UNIQUE INDEX uk_bucket_orcamento_usuario_subcategoria
    ON bucket_orcamento (tenant_id, subcategoria_id, usuario_id)
    WHERE usuario_id IS NOT NULL;

CREATE UNIQUE INDEX uk_bucket_orcamento_pessoa_subcategoria
    ON bucket_orcamento (tenant_id, subcategoria_id, pessoa_id)
    WHERE pessoa_id IS NOT NULL;

-- pessoa
CREATE INDEX idx_pessoa_tenant_id ON pessoa (tenant_id);

-- cartao: remove FK duplicada (cartao_principal_fk já cobre cartao_principal_id)
ALTER TABLE cartao DROP CONSTRAINT fkpixuyhfy72lrcj2p922uo5drd;
