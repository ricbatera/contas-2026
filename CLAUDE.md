# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Visão geral

Sistema de finanças pessoais (requisitos em `doc/PRD-financas-pessoais.md`). Repositório único com dois projetos:

- `backend/` — API REST Spring Boot 3.5 / Java 21 / Maven, pacote raiz `br.com.rdrtech`.
- `frontend/` — Angular 21 (componentes standalone, testes com Vitest).

Código, nomes de domínio, mensagens e commits são em português.

## Comandos

### Backend (`backend/`)

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=local   # usa application-local.properties (PostgreSQL local)
./mvnw spring-boot:run -Dspring-boot.run.profiles=h2      # usa application-h2.properties (H2)
./mvnw test                                               # só testes unitários (*Test, *Tests) via Surefire
./mvnw verify                                             # unitários + integração (*IT) via Failsafe; use este para validar mudanças
./mvnw test -Dtest=SaidaControllerIT                      # uma classe (com -Dtest o Surefire roda também *IT)
./mvnw test -Dtest=SaidaControllerIT#deveAceitarSaidaComSessentaParcelas   # um método
```

Sem perfil, `application.properties` exige as variáveis `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET` e `IA_CRYPTO_KEY`. `application-local.properties` e `application-h2.properties` são ignorados pelo git (contêm credenciais locais). O perfil `h2` desliga o Flyway e usa o schema gerado pelo Hibernate.

O PostgreSQL local roda no container Docker `postgres-financas` dentro do WSL (porta 5432, acessível do Windows), com os bancos `financas` (app) e `financas_test` (testes). Acesso via `wsl docker exec postgres-financas psql -U postgres -d financas`. A senha do banco de teste fica em `backend/src/test/resources/application-test-local.properties` (ignorado pelo git), ou nas variáveis `TEST_DB_URL`/`TEST_DB_USERNAME`/`TEST_DB_PASSWORD`. Se um teste falhar com `ClassFormatError`, rode `./mvnw clean test`: a IDE às vezes deixa classes corrompidas em `target/`.

Swagger: `/swagger-ui.html`.

### Frontend (`frontend/`)

```bash
npm start            # ng serve em :4200, com proxy de /api para http://localhost:8080 (proxy.conf.json)
npm run build
npm test             # ng test (Vitest + jsdom)
npx ng test --include src/app/core/saida.service.spec.ts   # um arquivo
```

### Docker (produção)

```bash
cp .env-prod.example .env-prod                       # preencha POSTGRES_PASSWORD, JWT_SECRET e IA_CRYPTO_KEY; o arquivo não é versionado
docker compose --env-file .env-prod up -d --build    # sobe db (postgres), backend e frontend (nginx)
```

O nginx do frontend serve o build do Angular e faz proxy de `/api/` para o backend, que fica na mesma origem, sem CORS. Só o frontend publica porta (`APP_PORT`, padrão 80). O banco usa o volume `pgdata` e o Flyway cria o schema na subida. O Docker roda no WSL: `wsl docker compose ...` a partir de `/mnt/c/projetos/minhasContas/2026-Novo`. Sem `--env-file .env-prod`, o compose falha de propósito.

### Publicação de imagens (Docker Hub)

```bash
cp scripts/docker/docker-publish.conf.example scripts/docker/docker-publish.conf   # credenciais; não versionado
./scripts/docker/publicar-backend.sh [major|minor|patch]      # Linux; padrão patch
.\scripts\docker\publicar-frontend.ps1 [major|minor|patch]    # Windows (usa `wsl docker` se não houver docker no PATH)
```

Cada script exige working tree limpo e constrói a imagem com a versão atual (`backend/pom.xml` ou `frontend/package.json`), com tags `:<versão>` e `:latest`. Depois faz o push, cria a tag git `<projeto>-v<versão>`, avança a versão, registra a publicação em `CHANGELOG.md` e commita. Não faz `git push`. A versão nos arquivos é sempre a próxima a publicar, então não a altere manualmente para baixo.

### Deploy remoto (SSH)

```bash
cp scripts/deploy/deploy.conf.example scripts/deploy/deploy.conf   # host, porta, usuário, chave ou senha, pasta; não versionado
./scripts/deploy/deploy.sh          # Linux (senha exige sshpass)
.\scripts\deploy\deploy.ps1         # Windows (senha usa plink/pscp se instalados; senão o ssh pede a senha)
```

O script pergunta as imagens (Enter = `:latest`; só uma tag, como `1.0.3`, completa o repositório), cria `REMOTE_PATH` no servidor se não existir e envia `scripts/deploy/docker-compose.deploy.yml` (imagens já substituídas, front em `18050`) e o `.env-prod`. No servidor, `scripts/deploy/remoto.sh` baixa o ambiente antigo (sem `-v`, o volume `pgdata` é preservado), remove as imagens antigas de backend/frontend, faz `pull` e `up -d --wait`.

### Acesso ao banco

```bash
./scripts/db/banco.sh <local|teste|remoto> <psql|pg_dump|pg_restore> [args]   # Git Bash ou Linux
```

`local`/`teste` usam o container `postgres-financas` do WSL (bancos `financas`/`financas_test`). `remoto` usa o serviço `db` do compose no servidor, via SSH com os dados de `scripts/deploy/deploy.conf`, em modo batch, e por isso exige `SSH_KEY` ou senha com `sshpass`/`plink`. O script já passa `-U`/`-d`, e stdin/stdout vão direto para a ferramenta. Dumps ficam em `backups/`, que é ignorado pelo git.

## Arquitetura do backend

**Organização por feature:** cada domínio (`saida`, `fatura`, `faturaimportacao`, `presaida`, `cartao`, `bucket`, `dashboard`, `categoria`, `subcategoria`, `pessoa`, `contabancaria`, `ia`, `usuario`, `auth`) é um pacote com Entidade, `*Controller`, `*Service`, `*Repository`, `*Mapper` e `dto/` (records `*Request`/`*Response`). Os pacotes genéricos `model/`, `repository/`, `service/`, `dto/` e `api/controller` estão vazios. Não coloque código novo neles.

**Multi-tenancy por usuário:** entidades de negócio estendem `tenant.TenantAwareEntity`, não `audit.BaseEntity` diretamente. Ela preenche `tenant_id` com o id do usuário autenticado no `@PrePersist` e aplica um `@Filter` Hibernate com `autoEnabled`, de modo que toda query dessas entidades é filtrada por `tenant_id` sem código nos repositórios. O Javadoc da classe explica por que **não** se usa `@TenantId`/`CurrentTenantIdentifierResolver`: isso quebra o boot. Toda tabela de negócio nova precisa da coluna `tenant_id UUID NOT NULL`. `Usuario`, `Role` e `RefreshToken` não são tenant-aware.

**Auditoria:** `BaseEntity` + `JpaAuditingConfig`/`AuditorAwareImpl` preenchem `created_at/by` e `updated_at/by`.

**Segurança:** JWT HS256 stateless via `spring-boot-starter-oauth2-resource-server` (`security/SecurityConfig`, `JwtService`). O claim `roles` vira `ROLE_*`. Rotas públicas: `/api/auth/**` (register/login/refresh), Swagger, `/actuator/health` e `/h2-console/**`. O id do usuário atual vem de `SecurityUtils.getCurrentUserId()`.

**Respostas e erros:** controllers retornam `SuccessResponse.of(data)`, e o JSON fica no formato `{ data, message, timestamp }`, por isso os testes leem `$.data...`. Erros de regra de negócio estendem `exception.BusinessException`, e cada subclasse define seu `HttpStatus` via `getStatus()`. O `GlobalExceptionHandler` converte tudo em `ErrorResponse`. Para um erro novo, crie uma subclasse em `exception/`, sem handler extra.

**Esquema do banco (Flyway):** o schema é versionado em `backend/src/main/resources/db/migration` e aplicado pelo Flyway na inicialização. O Hibernate só valida (`ddl-auto=validate`) em todos os perfis, exceto `h2`.
- Toda mudança de entidade exige uma nova migration `V{n}__descricao.sql` no mesmo commit. **Nunca edite uma migration já aplicada**, crie outra.
- V1 é o baseline: o schema do banco local em 2026-10-17. Bancos que já existiam sem `flyway_schema_history` são marcados na V1 sem executá-la (`baseline-on-migrate`).
- Os testes recriam o schema do zero (`TestFlywayConfig`: clean + migrate) no banco `financas_test`, então as migrations e as constraints são exercitadas em PostgreSQL real. Tabelas usadas só em teste ficam em `src/test/resources/db/testmigration`.
- Mantenha constraints iguais entre a validação do DTO e o SQL (ex.: parcelas entre 1 e 60).
- `scripts/legacy/` guarda os scripts anteriores ao Flyway, só como referência.

**Domínio central:** uma `Saida` (despesa) gera `SaidaParcela`s e pode ter vários `SaidaResponsavel` (usuário ou `Pessoa`). Compras no cartão são agrupadas em `Fatura` por competência, e cartões adicionais compartilham a fatura do cartão principal. `Bucket` é o orçamento mensal por subcategoria e responsável. `DashboardService` consolida saídas, buckets, contas fixas e projeção mensal (`/api/dashboard`, `/projecao`, `/saidas`, `/buckets`, `/contas-fixas`).

**IA (`ia/`):** cada usuário cadastra `ProvedorIa`s (OpenAI e compatíveis via `baseUrl`, Anthropic, Gemini, Ollama), e só um pode ser o padrão (índice único parcial). `ChatModelFactory` monta o `ChatModel` do LangChain4j em runtime pelos builders, com cache invalidado quando o registro muda. **Não use os starters Spring Boot do LangChain4j**, porque a configuração é dinâmica. A API key é cifrada com AES-GCM por `CifradorSegredos`, usando `ia.chave-criptografia` (`IA_CRYPTO_KEY`, base64 de 32 bytes). Trocar essa chave torna ilegíveis as chaves já gravadas. A API devolve só a chave mascarada. Para novos recursos de IA, use `ProvedorIaService.chatModelPadrao()`. Nos IT, substitua `ChatModelFactory` por `@MockitoBean` para não chamar provedores reais.

**Importação de fatura (`faturaimportacao/`, `presaida/`):** o PDF chega em base64 (`POST /api/faturas/importacoes`) e o `PdfFaturaLeitor` (PDFBox) o abre com a senha opcional, que não é gravada. A proteção é removida antes de gravar o PDF. O texto vai para a IA (`LeitorFaturaIa`), que devolve JSON. A conciliação (`ConciliacaoFaturaService`) não usa IA:
- identifica o cartão principal pelos 4 últimos dígitos;
- casa cada item com uma parcela da `Fatura` daquela competência pelo valor, o número e a quantidade de parcelas;
- quando casa, grava o texto do PDF em `Saida.descricaoFatura`;
- quando não casa, cria uma `PreSaida`, com `primeiroVencimento` recuado até a parcela 1.

Ao aprovar (`/api/pre-saidas/{id}/aprovar`), a pré-saída vira `Saida` via `SaidaService.criar`, que gera as parcelas retroativas. O upload valida arquivo e senha na hora e responde `202` com status `PROCESSANDO`. A leitura por IA e a conciliação rodam em segundo plano no `ExecutorImportacaoFatura`, uma fila própria em memória que repassa o `SecurityContext` para o filtro de tenant funcionar na outra thread. Reprocessar também é assíncrono. Reprocessar ou excluir durante o processamento dá 409. Na subida, `RecuperacaoImportacoesInterrompidas` marca como `ERRO` o que ficou em `PROCESSANDO`. No frontend, o `ImportacaoMonitorService` (iniciado pelo shell `Home`) consulta `GET /api/faturas/importacoes/{id}/status` a cada 3 s enquanto houver importação processando e mostra os alertas flutuantes (`pages/home/importacao-alertas`). Specs que renderizam o shell precisam responder `GET /api/faturas/importacoes`. A chamada à IA roda fora de transação. Uma falha na leitura não vira erro HTTP: a importação fica com status `ERRO` e pode ser reprocessada. O nginx aceita corpo de até 20 MB e timeout de 180 s em `/api/`.

**Cadastro de usuário:** `AuthService.register` chama `CategoriasPadraoService.criarPara`, que cria 19 categorias padrão (11 de despesa e 8 de receita) com subcategorias na mesma transação. Como não há usuário autenticado nesse momento, o tenant é atribuído com `TenantAwareEntity.definirTenant`. Use esse método sempre que gravar entidades tenant-aware sem JWT no contexto. Testes que criam categorias via API não podem reutilizar nomes padrão (ex.: "Moradia"), senão recebem 409.

**Testes:** `*Test` são testes unitários de service. `*IT` são `@SpringBootTest` + MockMvc ponta a ponta: registram um usuário novo por teste via `/api/auth/register` (e-mail com `System.nanoTime()`) e usam o `accessToken` retornado. Isso também isola os dados por tenant. `tenant/TenantFilterIT` cobre o isolamento.

## Arquitetura do frontend

- `src/app/core/`: um serviço HTTP por recurso do backend (`*.service.ts`, chamando `/api/...`), mais `auth.service`, `auth.guard` (`authGuard`/`guestGuard`) e `auth.interceptor`. O interceptor anexa o Bearer token e, em caso de 401, tenta `refresh()` uma vez antes de redirecionar para `/login`.
- `src/app/pages/`: telas por feature, normalmente um par `*-list` / `*-form`, onde o form serve para criar e editar via rota `:id/editar`.
- `app.routes.ts`: as páginas autenticadas são filhas lazy-loaded de `/home` (shell em `pages/home`).
- Locale `pt-BR` registrado em `app.config.ts`.
