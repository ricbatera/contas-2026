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
./mvnw test                                               # todos os testes (H2 em memória, perfil "test")
./mvnw test -Dtest=SaidaControllerIT                      # uma classe
./mvnw test -Dtest=SaidaControllerIT#deveAceitarSaidaComSessentaParcelas   # um método
```

Sem perfil, `application.properties` exige as variáveis `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` e `JWT_SECRET`. `application-local.properties` e `application-h2.properties` são ignorados pelo git (contêm credenciais locais).

Swagger: `/swagger-ui.html`.

### Frontend (`frontend/`)

```bash
npm start            # ng serve em :4200, com proxy de /api para http://localhost:8080 (proxy.conf.json)
npm run build
npm test             # ng test (Vitest + jsdom)
npx ng test --include src/app/core/saida.service.spec.ts   # um arquivo
```

## Arquitetura do backend

**Organização por feature:** cada domínio (`saida`, `fatura`, `cartao`, `bucket`, `dashboard`, `categoria`, `subcategoria`, `pessoa`, `contabancaria`, `usuario`, `auth`) é um pacote com Entidade, `*Controller`, `*Service`, `*Repository`, `*Mapper` e `dto/` (records `*Request`/`*Response`). Os pacotes genéricos `model/`, `repository/`, `service/`, `dto/` e `api/controller` estão vazios. Não coloque código novo neles.

**Multi-tenancy por usuário:** entidades de negócio estendem `tenant.TenantAwareEntity`, não `audit.BaseEntity` diretamente. Ela preenche `tenant_id` com o id do usuário autenticado no `@PrePersist` e aplica um `@Filter` Hibernate com `autoEnabled`, de modo que toda query dessas entidades é filtrada por `tenant_id` sem código nos repositórios. O Javadoc da classe explica por que **não** se usa `@TenantId`/`CurrentTenantIdentifierResolver`: isso quebra o boot. Toda tabela de negócio nova precisa da coluna `tenant_id UUID NOT NULL`. `Usuario`, `Role` e `RefreshToken` não são tenant-aware.

**Auditoria:** `BaseEntity` + `JpaAuditingConfig`/`AuditorAwareImpl` preenchem `created_at/by` e `updated_at/by`.

**Segurança:** JWT HS256 stateless via `spring-boot-starter-oauth2-resource-server` (`security/SecurityConfig`, `JwtService`). O claim `roles` vira `ROLE_*`. Rotas públicas: `/api/auth/**` (register/login/refresh), Swagger, `/actuator/health` e `/h2-console/**`. O id do usuário atual vem de `SecurityUtils.getCurrentUserId()`.

**Respostas e erros:** controllers retornam `SuccessResponse.of(data)`, e o JSON fica no formato `{ data, message, timestamp }`, por isso os testes leem `$.data...`. Erros de regra de negócio estendem `exception.BusinessException`, e cada subclasse define seu `HttpStatus` via `getStatus()`. O `GlobalExceptionHandler` converte tudo em `ErrorResponse`. Para um erro novo, crie uma subclasse em `exception/`, sem handler extra.

**Esquema do banco:** produção/local usa PostgreSQL com `ddl-auto=validate`, ou seja, o Hibernate **não** cria tabelas. Toda mudança de entidade exige um script SQL aplicado manualmente com `psql`: `backend/scripts/` (criação de tabelas e migrações pontuais) ou `scripts/` (schema de saídas/faturas, seeds). Não há Flyway/Liquibase. Os testes usam H2 com `create-drop`, então passar nos testes não garante que o script SQL esteja correto. Mantenha constraints (ex.: limites de parcelas) iguais entre a validação do DTO e o SQL.

**Domínio central:** uma `Saida` (despesa) gera `SaidaParcela`s e pode ter vários `SaidaResponsavel` (usuário ou `Pessoa`). Compras no cartão são agrupadas em `Fatura` por competência, e cartões adicionais compartilham a fatura do cartão principal. `Bucket` é o orçamento mensal por subcategoria e responsável. `DashboardService` consolida saídas, buckets, contas fixas e projeção mensal (`/api/dashboard`, `/projecao`, `/saidas`, `/buckets`, `/contas-fixas`).

**Testes:** `*Test` são testes unitários de service. `*IT` são `@SpringBootTest` + MockMvc ponta a ponta: registram um usuário novo por teste via `/api/auth/register` (e-mail com `System.nanoTime()`) e usam o `accessToken` retornado. Isso também isola os dados por tenant. `tenant/TenantFilterIT` cobre o isolamento.

## Arquitetura do frontend

- `src/app/core/`: um serviço HTTP por recurso do backend (`*.service.ts`, chamando `/api/...`), mais `auth.service`, `auth.guard` (`authGuard`/`guestGuard`) e `auth.interceptor`. O interceptor anexa o Bearer token e, em caso de 401, tenta `refresh()` uma vez antes de redirecionar para `/login`.
- `src/app/pages/`: telas por feature, normalmente um par `*-list` / `*-form`, onde o form serve para criar e editar via rota `:id/editar`.
- `app.routes.ts`: as páginas autenticadas são filhas lazy-loaded de `/home` (shell em `pages/home`).
- Locale `pt-BR` registrado em `app.config.ts`.
