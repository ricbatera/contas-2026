---
name: validar-build
description: Valida o build do projeto antes de publicar ou entregar - roda os testes unitários e de integração do backend (mvnw verify, com PostgreSQL de teste) e os testes e o build de produção do frontend (Vitest e ng build). Use antes de publicar imagens ou quando pedirem para "validar", "rodar os testes" ou "ver se o build passa".
---

# Validar o build

Rode as etapas abaixo e relate o resultado de cada uma. Backend e frontend são independentes: se o usuário pediu só um, valide só esse.

## 1. Pré-requisito do backend: PostgreSQL de teste
Os testes `*IT` usam o banco `financas_test` no container `postgres-financas` do WSL.

```powershell
wsl docker ps --filter name=postgres-financas --format '{{.Status}}'
```
Se não estiver `Up`, suba com `wsl docker start postgres-financas` e confira de novo.

## 2. Backend
Na pasta `backend/` (demora alguns minutos; timeout 600000 ms):

```powershell
.\mvnw.cmd verify
```
- Sucesso: `BUILD SUCCESS`. Relate o total de testes do Surefire (unitários) e do Failsafe (integração).
- `ClassFormatError`: classes corrompidas pela IDE em `target/`. Rode `.\mvnw.cmd clean verify`.
- Falha de conexão com o banco: volte à etapa 1. A senha de teste fica em `backend/src/test/resources/application-test-local.properties` (não exiba o conteúdo).
- Falha de teste: mostre o nome do teste e a mensagem de assert de `target/surefire-reports` ou `target/failsafe-reports`. Não altere testes só para fazê-los passar.

## 3. Frontend
Na pasta `frontend/`:

```powershell
npm test -- --watch=false
npm run build
```
- Relate quantos testes passaram e se o build de produção gerou `dist/frontend/browser`.
- Avisos de budget do Angular não são erro, mas mencione se aparecerem.

## 4. (Opcional) Build das imagens sem publicar
Se for pedido para validar também o Dockerfile:

```powershell
wsl docker build -t financas-backend:validacao /mnt/c/projetos/minhasContas/2026-Novo/backend
wsl docker build -t financas-frontend:validacao /mnt/c/projetos/minhasContas/2026-Novo/frontend
```
Remova as tags `:validacao` ao final (`wsl docker image rm ...`).

## Resultado
Termine com uma tabela curta: etapa, resultado (passou/falhou) e detalhe (nº de testes ou erro principal).
