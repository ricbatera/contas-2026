# Histórico de versões

Imagens publicadas no Docker Hub, da mais recente para a mais antiga. Gerado pelos scripts em `scripts/docker/`.

## backend 1.0.5 — 2026-10-30

- Imagem: `ricardo1782/financas-backend:1.0.5` (também `latest`)
- Commit: `0d070d1` (tag `backend-v1.0.5`)
- Próxima versão de desenvolvimento: 1.0.6
- Mudanças:
  - 44d970a feat: máscara pt-BR nos campos monetários e valor negativo como reembolso

## frontend 1.0.6 — 2026-10-30

- Imagem: `ricardo1782/financas-frontend:1.0.6` (também `latest`)
- Commit: `fe9fa8c` (tag `frontend-v1.0.6`)
- Próxima versão de desenvolvimento: 1.0.7
- Mudanças:
  - d5bec65 feat: gerar contas fixas do mês e card de contas fixas com geradas e previstas
  - c9ceb6a fix(frontend): maiúscula só na primeira letra das datas e meses

## backend 1.0.4 — 2026-10-30

- Imagem: `ricardo1782/financas-backend:1.0.4` (também `latest`)
- Commit: `d5bec65` (tag `backend-v1.0.4`)
- Próxima versão de desenvolvimento: 1.0.5
- Mudanças:
  - d5bec65 feat: gerar contas fixas do mês e card de contas fixas com geradas e previstas

## frontend 1.0.5 — 2026-10-25

- Imagem: `ricardo1782/financas-frontend:1.0.5` (também `latest`)
- Commit: `4ad77a2` (tag `frontend-v1.0.5`)
- Próxima versão de desenvolvimento: 1.0.6
- Mudanças:
  - 5347c18 feat: rate limit por IP e bloqueio temporário do login

## backend 1.0.3 — 2026-10-25

- Imagem: `ricardo1782/financas-backend:1.0.3` (também `latest`)
- Commit: `5347c18` (tag `backend-v1.0.3`)
- Próxima versão de desenvolvimento: 1.0.4
- Mudanças:
  - 5347c18 feat: rate limit por IP e bloqueio temporário do login

## frontend 1.0.4 — 2026-10-25

- Imagem: `ricardo1782/financas-frontend:1.0.4` (também `latest`)
- Commit: `da42e66` (tag `frontend-v1.0.4`)
- Próxima versão de desenvolvimento: 1.0.5
- Mudanças:
  - da42e66 feat(frontend): abrir as telas no mês seguinte depois do dia 10
  - d319e35 fix(frontend): combo de competência das faturas mostrava outro mês

## frontend 1.0.3 — 2026-10-19

- Imagem: `ricardo1782/financas-frontend:1.0.3` (também `latest`)
- Commit: `78ff0af` (tag `frontend-v1.0.3`)
- Próxima versão de desenvolvimento: 1.0.4
- Mudanças:
  - 50bb7c4 feat: ler comprovantes de maquininha de cartão com IA
  - 20fefbb fix(frontend): ícones cortados no menu lateral recolhido
  - f8766eb fix(frontend): seletor de cores nos formulários de cartão e conta
  - 0001f55 feat: listar modelos disponíveis no cadastro de provedores de IA
  - 5fb7bf5 feat(frontend): calculadora nos campos de valor
  - 4b10b4a feat: cadastro de entradas com recorrência e receitas no dashboard
  - 63d4ac1 feat(frontend): filtrar saídas pelo cartão principal com seus adicionais

## backend 1.0.2 — 2026-10-19

- Imagem: `ricardo1782/financas-backend:1.0.2` (também `latest`)
- Commit: `50bb7c4` (tag `backend-v1.0.2`)
- Próxima versão de desenvolvimento: 1.0.3
- Mudanças:
  - 50bb7c4 feat: ler comprovantes de maquininha de cartão com IA
  - a5412e2 fix: identificar o cartão quando a fatura mascara um dígito do final
  - 0001f55 feat: listar modelos disponíveis no cadastro de provedores de IA
  - 4b10b4a feat: cadastro de entradas com recorrência e receitas no dashboard

## frontend 1.0.2 — 2026-10-17

- Imagem: `ricardo1782/financas-frontend:1.0.2` (também `latest`)
- Commit: `d11c69d` (tag `frontend-v1.0.2`)
- Próxima versão de desenvolvimento: 1.0.3
- Mudanças:
  - d11c69d feat(frontend): cadastro rápido de cartão, categoria, subcategoria e pessoa

## frontend 1.0.1 — 2026-10-17

- Imagem: `ricardo1782/financas-frontend:1.0.1` (também `latest`)
- Commit: `8cd5d9b` (tag `frontend-v1.0.1`)
- Próxima versão de desenvolvimento: 1.0.2
- Mudanças:
  - 04817e9 feat(fatura): conciliação por valor, desempate por IA e vínculo de pré-saídas
  - ba0c00a feat(fatura): importação de fatura em PDF com IA e pré-saídas
  - 0af33aa feat(ia): cadastro de provedores de IA e tela de teste de integração

## backend 1.0.1 — 2026-10-17

- Imagem: `ricardo1782/financas-backend:1.0.1` (também `latest`)
- Commit: `59ce64b` (tag `backend-v1.0.1`)
- Próxima versão de desenvolvimento: 1.0.2
- Mudanças:
  - 59ce64b fix(fatura): leitura completa de faturas em colunas e parcelas conferidas no texto
  - 04817e9 feat(fatura): conciliação por valor, desempate por IA e vínculo de pré-saídas
  - ba0c00a feat(fatura): importação de fatura em PDF com IA e pré-saídas
  - 0af33aa feat(ia): cadastro de provedores de IA e tela de teste de integração

## frontend 1.0.0 — 2026-10-17

- Imagem: `ricardo1782/financas-frontend:1.0.0` (também `latest`)
- Commit: `e21f837` (tag `frontend-v1.0.0`)
- Próxima versão de desenvolvimento: 1.0.1
- Mudanças:
  - b96efd7 feat: scripts de build e publicação das imagens no Docker Hub
  - 563e414 feat: dockerizar frontend, backend e banco com Docker Compose
  - 6c7bcd0 chore: incorporar frontend ao repositório único
  - 32236a5 inicio do projeto

## backend 1.0.0 — 2026-10-17

- Imagem: `ricardo1782/financas-backend:1.0.0` (também `latest`)
- Commit: `15f107d` (tag `backend-v1.0.0`)
- Próxima versão de desenvolvimento: 1.0.1
- Mudanças:
  - 563e414 feat: dockerizar frontend, backend e banco com Docker Compose
  - 85264e0 feat: criar categorias padrão no cadastro de novos usuários
  - 401eb5d feat: adotar Flyway para versionar o schema do banco
  - 19b30aa feat: adicionar buckets de orçamento e dashboard; ampliar parcelas para 60
  - c468eb6 incluido diversos recursos
  - b6486db retomada do projeto de novas contas
  - e3e6894 feat: implementar gerenciamento de cartões com CRUD e validações
  - ecd21cb feat: implement JWT authentication and user management
  - ea2631b adiciona configurações de banco de dados e propriedades para testes
  - b39a630 adiciona classes ErrorResponse e SuccessResponse para gerenciamento de respostas da API
  - 32236a5 inicio do projeto
