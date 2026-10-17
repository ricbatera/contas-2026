#!/usr/bin/env bash
# Constrói a imagem do backend com a versão do pom.xml, publica no Docker Hub,
# cria a tag git, avança a versão e registra no CHANGELOG.md.
# Uso: ./scripts/docker/publicar-backend.sh [major|minor|patch]   (padrão: patch)

PROJETO=backend
IMAGEM_CHAVE=BACKEND_IMAGE
ARQUIVOS_VERSAO=(backend/pom.xml)

# A versão do projeto é a primeira <version> depois de <artifactId>financas</artifactId>
# (a do parent vem antes e é ignorada).
ler_versao() {
  awk '
    /<artifactId>financas<\/artifactId>/ { achou = 1; next }
    achou && /<version>/ { sub(/.*<version>/, ""); sub(/<\/version>.*/, ""); gsub(/\r/, ""); print; exit }
  ' "$RAIZ/backend/pom.xml"
}

gravar_versao() {
  local tmp
  tmp="$(mktemp)"
  awk -v nova="$1" '
    achou && !feito && /<version>/ { sub(/<version>[^<]*<\/version>/, "<version>" nova "</version>"); feito = 1 }
    /<artifactId>financas<\/artifactId>/ { achou = 1 }
    { print }
  ' "$RAIZ/backend/pom.xml" > "$tmp"
  cat "$tmp" > "$RAIZ/backend/pom.xml"
  rm -f "$tmp"
}

source "$(dirname "${BASH_SOURCE[0]}")/comum.sh"
publicar "$@"
