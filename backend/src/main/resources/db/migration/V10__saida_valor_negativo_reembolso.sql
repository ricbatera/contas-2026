-- Valores negativos representam reembolsos (créditos) em fatura; só o zero continua proibido.

ALTER TABLE saida
    DROP CONSTRAINT ck_saida_valor_por_parcela,
    ADD CONSTRAINT ck_saida_valor_por_parcela CHECK (valor_por_parcela <> 0);

ALTER TABLE saida_parcela
    DROP CONSTRAINT ck_saida_parcela_valor,
    ADD CONSTRAINT ck_saida_parcela_valor CHECK (valor <> 0);

ALTER TABLE saida_responsavel
    DROP CONSTRAINT ck_saida_responsavel_valor,
    ADD CONSTRAINT ck_saida_responsavel_valor CHECK (valor_por_parcela <> 0);

ALTER TABLE pre_saida
    DROP CONSTRAINT ck_pre_saida_valor,
    ADD CONSTRAINT ck_pre_saida_valor CHECK (valor_por_parcela <> 0);
