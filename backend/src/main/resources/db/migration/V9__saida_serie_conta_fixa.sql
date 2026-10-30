-- Liga as ocorrências mensais da mesma conta fixa, para gerar o mês seguinte sem duplicar.
ALTER TABLE saida ADD COLUMN serie_conta_fixa_id uuid;

-- Cada conta fixa existente começa a própria série.
UPDATE saida SET serie_conta_fixa_id = id WHERE conta_fixa;

CREATE INDEX idx_saida_serie_conta_fixa ON saida (tenant_id, serie_conta_fixa_id);
