# Histórico de versões

Imagens publicadas no Docker Hub, da mais recente para a mais antiga. Gerado pelos scripts em `scripts/docker/`.

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
