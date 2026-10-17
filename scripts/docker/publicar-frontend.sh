#!/usr/bin/env bash
# Constrói a imagem do frontend com a versão do package.json, publica no Docker Hub,
# cria a tag git, avança a versão e registra no CHANGELOG.md.
# Uso: ./scripts/docker/publicar-frontend.sh [major|minor|patch]   (padrão: patch)

PROJETO=frontend
IMAGEM_CHAVE=FRONTEND_IMAGE
ARQUIVOS_VERSAO=(frontend/package.json frontend/package-lock.json)

ler_versao() {
  (cd "$RAIZ/frontend" && node -p "require('./package.json').version")
}

# npm version atualiza package.json e package-lock.json juntos.
gravar_versao() {
  (cd "$RAIZ/frontend" && npm version "$1" --no-git-tag-version >/dev/null)
}

source "$(dirname "${BASH_SOURCE[0]}")/comum.sh"
exigir_comando node
exigir_comando npm
publicar "$@"
