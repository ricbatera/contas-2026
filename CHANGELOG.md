# Histórico de versões

Imagens publicadas no Docker Hub, da mais recente para a mais antiga. Gerado pelos scripts em `scripts/docker/`.

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
