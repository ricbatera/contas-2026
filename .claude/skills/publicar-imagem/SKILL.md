---
name: publicar-imagem
description: Gera e publica no Docker Hub as imagens do backend e/ou frontend usando os scripts de scripts/docker - a imagem leva a versão do pom.xml/package.json, recebe as tags versão e latest, e o script cria a tag git, avança a versão e registra no CHANGELOG.md. Use quando pedirem para "publicar", "gerar imagem", "subir imagem pro Docker Hub" ou "fazer release".
argument-hint: "[backend|frontend|ambos] [patch|minor|major]"
---

# Publicar imagem no Docker Hub

Argumentos: projeto (`backend`, `frontend` ou `ambos`; padrão `ambos`) e incremento da próxima versão (`patch`, `minor` ou `major`; padrão `patch`). O incremento define a versão **seguinte**: a imagem publicada sempre leva a versão que já está no arquivo.

## 1. Pré-condições (verifique todas antes de qualquer build)
1. `scripts/docker/docker-publish.conf` existe. Se não existir, peça ao usuário para copiar `docker-publish.conf.example` e preencher. Não leia nem exiba o token.
2. Working tree limpo: `git status --porcelain` vazio. Se houver mudanças, pare e pergunte ao usuário o que fazer. Não faça commit nem stash por conta própria.
3. Versões atuais:
   - backend: a `<version>` logo após `<artifactId>financas</artifactId>` em `backend/pom.xml` (não a do parent);
   - frontend: `version` em `frontend/package.json`.
4. A tag `<projeto>-v<versão>` não existe (`git tag --list`). Se existir, a versão já foi publicada: avise e pare.
5. Build validado: use a skill `validar-build` para o(s) projeto(s), a menos que o usuário tenha dispensado. Não publique com testes falhando.

## 2. Confirmação
Mostre ao usuário e espere aprovação explícita:
- imagens que serão enviadas: `<namespace>/<imagem>:<versão>` e `:latest` (namespace e nomes do conf; só nomes, sem credenciais);
- próxima versão após o incremento;
- que serão criados uma tag git e um commit local.

## 3. Execução (Windows: PowerShell)
Um projeto por vez, backend primeiro. O build leva minutos (timeout 600000 ms):

```powershell
.\scripts\docker\publicar-backend.ps1 patch
.\scripts\docker\publicar-frontend.ps1 patch
```
(No Linux: `./scripts/docker/publicar-backend.sh patch`.)

O script para sozinho se o build ou o push falhar, sem alterar versão nem criar tag. Nesse caso mostre o erro:
- `docker login` falhou: token inválido, expirado ou sem permissão de escrita. O usuário precisa revisar o conf.
- push negado: o repositório não existe no namespace ou o token não tem acesso a ele.
- erro de build: mostre o passo do Dockerfile que falhou.

## 4. Após o sucesso
1. Confira: `git log --oneline -2`, `git tag --list '<projeto>-v*'` e a nova entrada no topo do `CHANGELOG.md`.
2. Relate: imagem e versão publicadas, tag criada e nova versão de desenvolvimento.
3. O script **não** faz `git push`. Pergunte se o usuário quer enviar com `git push --follow-tags` e só execute com o "sim".
4. Se a próxima etapa for deploy, sugira a skill `deploy-remoto` com as versões recém-publicadas.
