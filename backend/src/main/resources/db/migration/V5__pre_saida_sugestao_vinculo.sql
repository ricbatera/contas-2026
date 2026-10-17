-- Pré-saída pode sugerir uma saída já lançada (mesmo valor, outra numeração ou mês vizinho,
-- ou indicada pela IA). Confirmada pelo usuário, ela fica VINCULADA à saída em vez de criar outra.
ALTER TABLE pre_saida
    ADD COLUMN saida_sugerida_id uuid,
    ADD COLUMN motivo_sugestao character varying(500),
    ADD CONSTRAINT fk_pre_saida_saida_sugerida FOREIGN KEY (saida_sugerida_id) REFERENCES saida (id) ON DELETE SET NULL;

ALTER TABLE pre_saida DROP CONSTRAINT ck_pre_saida_status;
ALTER TABLE pre_saida
    ADD CONSTRAINT ck_pre_saida_status CHECK (status IN ('PENDENTE', 'APROVADA', 'REJEITADA', 'VINCULADA'));
