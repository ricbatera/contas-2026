-- "É uma compra nova": guarda a sugestão recusada pelo usuário para o reprocessamento da fatura
-- não vincular de novo a pré-saída àquela saída.
ALTER TABLE pre_saida
    ADD COLUMN saida_descartada_id uuid,
    ADD CONSTRAINT fk_pre_saida_saida_descartada FOREIGN KEY (saida_descartada_id) REFERENCES saida (id) ON DELETE SET NULL;
