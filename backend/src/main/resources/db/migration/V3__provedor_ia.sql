-- Provedores de IA configurados por usuário (OpenAI e compatíveis, Anthropic, Gemini, Ollama).
-- A API key é gravada cifrada (AES-GCM) pela aplicação; nunca em texto puro.
CREATE TABLE provedor_ia (
    id uuid NOT NULL,
    created_at timestamp(6) without time zone,
    created_by character varying(120),
    updated_at timestamp(6) without time zone,
    updated_by character varying(120),
    tenant_id uuid NOT NULL,
    nome character varying(50) NOT NULL,
    tipo character varying(20) NOT NULL,
    modelo character varying(100) NOT NULL,
    base_url character varying(255),
    api_key_cifrada text,
    temperatura numeric(3,2),
    max_tokens integer,
    timeout_segundos integer NOT NULL,
    padrao boolean NOT NULL,
    ativo boolean NOT NULL,
    CONSTRAINT provedor_ia_pkey PRIMARY KEY (id),
    CONSTRAINT fk_provedor_ia_tenant FOREIGN KEY (tenant_id) REFERENCES usuario (id),
    CONSTRAINT provedor_ia_tipo_check CHECK (tipo IN ('OPENAI', 'ANTHROPIC', 'GEMINI', 'OLLAMA')),
    CONSTRAINT provedor_ia_temperatura_check CHECK (temperatura IS NULL OR (temperatura >= 0 AND temperatura <= 2)),
    CONSTRAINT provedor_ia_max_tokens_check CHECK (max_tokens IS NULL OR (max_tokens >= 1 AND max_tokens <= 200000)),
    CONSTRAINT provedor_ia_timeout_check CHECK (timeout_segundos >= 5 AND timeout_segundos <= 300)
);

CREATE UNIQUE INDEX ux_provedor_ia_tenant_nome ON provedor_ia (tenant_id, lower(nome));

-- No máximo um provedor padrão por usuário.
CREATE UNIQUE INDEX ux_provedor_ia_tenant_padrao ON provedor_ia (tenant_id) WHERE padrao;
