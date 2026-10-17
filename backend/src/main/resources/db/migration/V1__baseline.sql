-- Baseline: schema do banco local em 2026-10-17, gerado com pg_dump --schema-only.
-- Bancos já existentes são marcados nesta versão via baseline-on-migrate, sem executá-la.
CREATE TABLE bucket_orcamento (
    id uuid NOT NULL,
    created_at timestamp(6) without time zone,
    created_by character varying(120),
    updated_at timestamp(6) without time zone,
    updated_by character varying(120),
    tenant_id uuid NOT NULL,
    valor_mensal numeric(12,2) NOT NULL,
    pessoa_id uuid,
    subcategoria_id uuid NOT NULL,
    usuario_id uuid,
    CONSTRAINT bucket_orcamento_check CHECK ((((usuario_id IS NOT NULL) AND (pessoa_id IS NULL)) OR ((usuario_id IS NULL) AND (pessoa_id IS NOT NULL))))
);

CREATE TABLE cartao (
    id uuid NOT NULL,
    created_at timestamp(6) without time zone,
    created_by character varying(120),
    updated_at timestamp(6) without time zone,
    updated_by character varying(120),
    banco character varying(100) NOT NULL,
    bandeira character varying(20) NOT NULL,
    cor character varying(7),
    nome character varying(50) NOT NULL,
    tenant_id uuid NOT NULL,
    ultimos_digitos character varying(4) NOT NULL,
    cartao_principal_id uuid,
    tipo character varying(20) DEFAULT 'PRINCIPAL'::character varying NOT NULL,
    virtual boolean DEFAULT false NOT NULL,
    ativo boolean DEFAULT true NOT NULL,
    CONSTRAINT cartao_associacao_tipo_valida CHECK (((((tipo)::text = 'PRINCIPAL'::text) AND (cartao_principal_id IS NULL)) OR (((tipo)::text = 'ADICIONAL'::text) AND (cartao_principal_id IS NOT NULL)))),
    CONSTRAINT cartao_tipo_valido CHECK (((tipo)::text = ANY ((ARRAY['PRINCIPAL'::character varying, 'ADICIONAL'::character varying])::text[])))
);

CREATE TABLE categoria (
    id uuid NOT NULL,
    created_at timestamp(6) without time zone,
    created_by character varying(120),
    updated_at timestamp(6) without time zone,
    updated_by character varying(120),
    tenant_id uuid NOT NULL,
    ativo boolean NOT NULL,
    nome character varying(100) NOT NULL,
    tipo character varying(20) NOT NULL,
    CONSTRAINT categoria_tipo_check CHECK (((tipo)::text = ANY ((ARRAY['DESPESA'::character varying, 'RECEITA'::character varying])::text[])))
);

CREATE TABLE conta_bancaria (
    id uuid NOT NULL,
    created_at timestamp(6) without time zone,
    created_by character varying(120),
    updated_at timestamp(6) without time zone,
    updated_by character varying(120),
    tenant_id uuid NOT NULL,
    agencia character varying(10),
    ativo boolean NOT NULL,
    banco character varying(100) NOT NULL,
    cor character varying(7),
    nome character varying(50) NOT NULL,
    numero_conta character varying(20),
    saldo_inicial numeric(15,2) NOT NULL,
    tipo_conta character varying(20) NOT NULL,
    CONSTRAINT conta_bancaria_tipo_conta_check CHECK (((tipo_conta)::text = ANY ((ARRAY['CORRENTE'::character varying, 'POUPANCA'::character varying, 'CARTEIRA'::character varying, 'INVESTIMENTO'::character varying, 'OUTRO'::character varying])::text[])))
);

CREATE TABLE fatura (
    id uuid NOT NULL,
    created_at timestamp(6) without time zone,
    created_by character varying(120),
    updated_at timestamp(6) without time zone,
    updated_by character varying(120),
    tenant_id uuid NOT NULL,
    competencia integer NOT NULL,
    vencimento date NOT NULL,
    cartao_principal_id uuid NOT NULL
);

CREATE TABLE pessoa (
    id uuid NOT NULL,
    created_at timestamp(6) without time zone,
    created_by character varying(120),
    updated_at timestamp(6) without time zone,
    updated_by character varying(120),
    tenant_id uuid NOT NULL,
    ativo boolean NOT NULL,
    nome character varying(100) NOT NULL
);

CREATE TABLE refresh_token (
    id uuid NOT NULL,
    data_criacao timestamp(6) with time zone NOT NULL,
    data_expiracao timestamp(6) with time zone NOT NULL,
    data_revogacao timestamp(6) with time zone,
    revogado boolean NOT NULL,
    usuario_id uuid NOT NULL
);

CREATE TABLE role (
    id uuid NOT NULL,
    created_at timestamp(6) without time zone,
    created_by character varying(120),
    updated_at timestamp(6) without time zone,
    updated_by character varying(120),
    nome character varying(30) NOT NULL,
    CONSTRAINT role_nome_check CHECK (((nome)::text = ANY ((ARRAY['USER'::character varying, 'ADMIN'::character varying])::text[])))
);

CREATE TABLE saida (
    id uuid NOT NULL,
    created_at timestamp(6) without time zone,
    created_by character varying(120),
    updated_at timestamp(6) without time zone,
    updated_by character varying(120),
    tenant_id uuid NOT NULL,
    data_inicial date,
    descricao character varying(120) NOT NULL,
    descricao_fatura character varying(120),
    forma_pagamento character varying(20) NOT NULL,
    primeiro_vencimento date,
    quantidade_parcelas integer NOT NULL,
    valor_por_parcela numeric(12,2) NOT NULL,
    cartao_id uuid,
    categoria_id uuid NOT NULL,
    subcategoria_id uuid NOT NULL,
    modo_responsabilidade character varying(20) NOT NULL,
    conta_fixa boolean DEFAULT false NOT NULL,
    CONSTRAINT saida_forma_pagamento_check CHECK (((forma_pagamento)::text = ANY ((ARRAY['CARTAO_CREDITO'::character varying, 'BOLETO'::character varying, 'DEBITO'::character varying, 'PIX'::character varying])::text[]))),
    CONSTRAINT saida_modo_responsabilidade_check CHECK (((modo_responsabilidade)::text = ANY ((ARRAY['USUARIO'::character varying, 'PESSOA'::character varying, 'DIVIDIR'::character varying])::text[])))
);

CREATE TABLE saida_parcela (
    id uuid NOT NULL,
    created_at timestamp(6) without time zone,
    created_by character varying(120),
    updated_at timestamp(6) without time zone,
    updated_by character varying(120),
    tenant_id uuid NOT NULL,
    numero integer NOT NULL,
    valor numeric(12,2) NOT NULL,
    vencimento date NOT NULL,
    fatura_id uuid,
    saida_id uuid NOT NULL
);

CREATE TABLE saida_responsavel (
    id uuid NOT NULL,
    created_at timestamp(6) without time zone,
    created_by character varying(120),
    updated_at timestamp(6) without time zone,
    updated_by character varying(120),
    tenant_id uuid NOT NULL,
    valor_por_parcela numeric(12,2) NOT NULL,
    pessoa_id uuid,
    saida_id uuid NOT NULL,
    usuario_id uuid,
    CONSTRAINT saida_responsavel_check CHECK ((((usuario_id IS NOT NULL) AND (pessoa_id IS NULL)) OR ((usuario_id IS NULL) AND (pessoa_id IS NOT NULL))))
);

CREATE TABLE subcategoria (
    id uuid NOT NULL,
    created_at timestamp(6) without time zone,
    created_by character varying(120),
    updated_at timestamp(6) without time zone,
    updated_by character varying(120),
    tenant_id uuid NOT NULL,
    ativo boolean NOT NULL,
    nome character varying(100) NOT NULL,
    categoria_id uuid NOT NULL,
    tipo character varying(20) NOT NULL,
    padrao boolean DEFAULT false NOT NULL,
    CONSTRAINT ck_subcategoria_tipo CHECK (((tipo)::text = ANY ((ARRAY['DESPESA'::character varying, 'RECEITA'::character varying])::text[])))
);

CREATE TABLE usuario (
    id uuid NOT NULL,
    created_at timestamp(6) without time zone,
    created_by character varying(120),
    updated_at timestamp(6) without time zone,
    updated_by character varying(120),
    ativo boolean NOT NULL,
    email character varying(180) NOT NULL,
    email_verificado boolean NOT NULL,
    nome character varying(100) NOT NULL,
    senha character varying(255) NOT NULL,
    sobrenome character varying(100) NOT NULL,
    telefone character varying(20)
);

CREATE TABLE usuario_role (
    usuario_id uuid NOT NULL,
    role_id uuid NOT NULL
);

ALTER TABLE ONLY bucket_orcamento
    ADD CONSTRAINT bucket_orcamento_pkey PRIMARY KEY (id);

ALTER TABLE ONLY cartao
    ADD CONSTRAINT cartao_current_pkey PRIMARY KEY (id);

ALTER TABLE ONLY categoria
    ADD CONSTRAINT categoria_pkey PRIMARY KEY (id);

ALTER TABLE ONLY conta_bancaria
    ADD CONSTRAINT conta_bancaria_pkey PRIMARY KEY (id);

ALTER TABLE ONLY fatura
    ADD CONSTRAINT fatura_pkey PRIMARY KEY (id);

ALTER TABLE ONLY pessoa
    ADD CONSTRAINT pessoa_pkey PRIMARY KEY (id);

ALTER TABLE ONLY refresh_token
    ADD CONSTRAINT refresh_token_pkey PRIMARY KEY (id);

ALTER TABLE ONLY role
    ADD CONSTRAINT role_pkey PRIMARY KEY (id);

ALTER TABLE ONLY saida_parcela
    ADD CONSTRAINT saida_parcela_pkey PRIMARY KEY (id);

ALTER TABLE ONLY saida
    ADD CONSTRAINT saida_pkey PRIMARY KEY (id);

ALTER TABLE ONLY saida_responsavel
    ADD CONSTRAINT saida_responsavel_pkey PRIMARY KEY (id);

ALTER TABLE ONLY subcategoria
    ADD CONSTRAINT subcategoria_pkey PRIMARY KEY (id);

ALTER TABLE ONLY usuario
    ADD CONSTRAINT uk5171l57faosmj8myawaucatdw UNIQUE (email);

ALTER TABLE ONLY categoria
    ADD CONSTRAINT uk_categoria_tenant_tipo_nome UNIQUE (tenant_id, tipo, nome);

ALTER TABLE ONLY fatura
    ADD CONSTRAINT uk_fatura_cartao_competencia UNIQUE (tenant_id, cartao_principal_id, competencia);

ALTER TABLE ONLY saida_parcela
    ADD CONSTRAINT uk_saida_parcela_numero UNIQUE (saida_id, numero);

ALTER TABLE ONLY saida_responsavel
    ADD CONSTRAINT uk_saida_responsavel_usuario UNIQUE (saida_id, usuario_id);

ALTER TABLE ONLY subcategoria
    ADD CONSTRAINT uk_subcategoria_categoria_nome UNIQUE (categoria_id, nome);

ALTER TABLE ONLY role
    ADD CONSTRAINT ukpsbnsrja0jvuncak7b0sqo2fi UNIQUE (nome);

ALTER TABLE ONLY usuario
    ADD CONSTRAINT usuario_pkey PRIMARY KEY (id);

ALTER TABLE ONLY usuario_role
    ADD CONSTRAINT usuario_role_pkey PRIMARY KEY (usuario_id, role_id);

CREATE INDEX idx_categoria_tenant_id ON categoria USING btree (tenant_id);

CREATE INDEX idx_fatura_cartao_competencia ON fatura USING btree (cartao_principal_id, competencia);

CREATE INDEX idx_fatura_tenant_id ON fatura USING btree (tenant_id);

CREATE INDEX idx_saida_cartao_id ON saida USING btree (cartao_id);

CREATE INDEX idx_saida_categoria_id ON saida USING btree (categoria_id);

CREATE INDEX idx_saida_parcela_fatura_id ON saida_parcela USING btree (fatura_id);

CREATE INDEX idx_saida_parcela_tenant_id ON saida_parcela USING btree (tenant_id);

CREATE INDEX idx_saida_parcela_vencimento ON saida_parcela USING btree (vencimento);

CREATE INDEX idx_saida_responsavel_pessoa_id ON saida_responsavel USING btree (pessoa_id);

CREATE INDEX idx_saida_responsavel_tenant_id ON saida_responsavel USING btree (tenant_id);

CREATE INDEX idx_saida_subcategoria_id ON saida USING btree (subcategoria_id);

CREATE INDEX idx_saida_tenant_id ON saida USING btree (tenant_id);

CREATE INDEX idx_subcategoria_categoria_id ON subcategoria USING btree (categoria_id);

CREATE INDEX idx_subcategoria_tenant_id ON subcategoria USING btree (tenant_id);

CREATE UNIQUE INDEX uk_categoria_tenant_tipo_nome_ci ON categoria USING btree (tenant_id, tipo, lower(btrim((nome)::text)));

CREATE UNIQUE INDEX uk_subcategoria_categoria_nome_ci ON subcategoria USING btree (categoria_id, lower(btrim((nome)::text)));

CREATE UNIQUE INDEX uk_subcategoria_tenant_tipo_padrao ON subcategoria USING btree (tenant_id, tipo) WHERE (padrao = true);

ALTER TABLE ONLY cartao
    ADD CONSTRAINT cartao_principal_fk FOREIGN KEY (cartao_principal_id) REFERENCES cartao(id);

ALTER TABLE ONLY saida_responsavel
    ADD CONSTRAINT fk3qj1v9twl8evt442ofa4x8tbr FOREIGN KEY (pessoa_id) REFERENCES pessoa(id);

ALTER TABLE ONLY saida
    ADD CONSTRAINT fk6kgisvskorw5hs75q1k62at4m FOREIGN KEY (categoria_id) REFERENCES categoria(id);

ALTER TABLE ONLY saida
    ADD CONSTRAINT fk9c8nvluqryc2xhejys8jpuuld FOREIGN KEY (subcategoria_id) REFERENCES subcategoria(id);

ALTER TABLE ONLY saida_parcela
    ADD CONSTRAINT fkb7mllf3gec1susioebxxou7uw FOREIGN KEY (saida_id) REFERENCES saida(id);

ALTER TABLE ONLY bucket_orcamento
    ADD CONSTRAINT fkbxthbxsn5nt4j26ntbyrj42bx FOREIGN KEY (pessoa_id) REFERENCES pessoa(id);

ALTER TABLE ONLY usuario_role
    ADD CONSTRAINT fke7gfguqsiox6p89xggm8g2twf FOREIGN KEY (role_id) REFERENCES role(id);

ALTER TABLE ONLY bucket_orcamento
    ADD CONSTRAINT fkeog1dqjslkhwfda7vjjwku9k1 FOREIGN KEY (usuario_id) REFERENCES usuario(id);

ALTER TABLE ONLY saida
    ADD CONSTRAINT fkkox94tk0eaw9t8u2jn264v7xt FOREIGN KEY (cartao_id) REFERENCES cartao(id);

ALTER TABLE ONLY saida_responsavel
    ADD CONSTRAINT fkm0au7f61pujhy7avfls09p6p FOREIGN KEY (usuario_id) REFERENCES usuario(id);

ALTER TABLE ONLY subcategoria
    ADD CONSTRAINT fkmh7cb7m4a5vvvhg5js699qnxk FOREIGN KEY (categoria_id) REFERENCES categoria(id);

ALTER TABLE ONLY saida_responsavel
    ADD CONSTRAINT fkmm6v1xyhk9mx95tygcrsb2i50 FOREIGN KEY (saida_id) REFERENCES saida(id);

ALTER TABLE ONLY usuario_role
    ADD CONSTRAINT fkpc2qjts6sqq4hja9f6i3hf0ep FOREIGN KEY (usuario_id) REFERENCES usuario(id);

ALTER TABLE ONLY cartao
    ADD CONSTRAINT fkpixuyhfy72lrcj2p922uo5drd FOREIGN KEY (cartao_principal_id) REFERENCES cartao(id);

ALTER TABLE ONLY refresh_token
    ADD CONSTRAINT fkpr5fysa08n2o6i1rxjumfl89q FOREIGN KEY (usuario_id) REFERENCES usuario(id);

ALTER TABLE ONLY bucket_orcamento
    ADD CONSTRAINT fkrdkh1plftbm8mnww55ig8h9fu FOREIGN KEY (subcategoria_id) REFERENCES subcategoria(id);

ALTER TABLE ONLY saida_parcela
    ADD CONSTRAINT fksn6f2x3eapxm7lk10lvqvuk3o FOREIGN KEY (fatura_id) REFERENCES fatura(id);

ALTER TABLE ONLY fatura
    ADD CONSTRAINT fktbabfx0k60gy0qe4i692at3wl FOREIGN KEY (cartao_principal_id) REFERENCES cartao(id);

