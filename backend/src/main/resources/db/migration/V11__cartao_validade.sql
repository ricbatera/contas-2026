-- Cartões virtuais temporários (uso único ou por período): a validade tira o cartão da seleção
-- de novas saídas e comprovantes depois da data, mas a importação de fatura continua casando com ele.

ALTER TABLE cartao
    ADD COLUMN valido_ate DATE,
    ADD CONSTRAINT ck_cartao_validade_virtual CHECK (valido_ate IS NULL OR virtual);
