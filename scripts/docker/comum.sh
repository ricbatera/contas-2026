# Funções compartilhadas pelos scripts publicar-*.sh. Não execute diretamente.
#
# O script que faz o source deve definir antes:
#   PROJETO                nome do projeto e do diretório (backend|frontend)
#   IMAGEM_CHAVE           chave do conf com o nome da imagem (BACKEND_IMAGE|FRONTEND_IMAGE)
#   ARQUIVOS_VERSAO        array com os arquivos alterados ao avançar a versão (relativos à raiz)
#   ler_versao             função que imprime a versão atual
#   gravar_versao <nova>   função que grava a nova versão

set -euo pipefail

RAIZ="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
CONF="$RAIZ/scripts/docker/docker-publish.conf"
CHANGELOG="$RAIZ/CHANGELOG.md"

erro() {
  echo "ERRO: $*" >&2
  exit 1
}

info() {
  echo "==> $*"
}

exigir_comando() {
  command -v "$1" >/dev/null 2>&1 || erro "comando '$1' não encontrado no PATH."
}

# Lê o conf linha a linha (sem source, para não executar nada do arquivo).
carregar_conf() {
  [ -f "$CONF" ] || erro "arquivo $CONF não encontrado. Copie docker-publish.conf.example para docker-publish.conf e preencha."
  local linha chave valor
  while IFS= read -r linha || [ -n "$linha" ]; do
    linha="${linha%$'\r'}"
    [[ "$linha" =~ ^[[:space:]]*(#|$) ]] && continue
    [[ "$linha" == *=* ]] || continue
    chave="$(echo "${linha%%=*}" | xargs)"
    valor="${linha#*=}"
    valor="${valor#"${valor%%[![:space:]]*}"}"
    valor="${valor%"${valor##*[![:space:]]}"}"
    valor="${valor%\"}"; valor="${valor#\"}"
    printf -v "CONF_$chave" '%s' "$valor"
  done < "$CONF"

  DOCKERHUB_USERNAME="${CONF_DOCKERHUB_USERNAME:-}"
  DOCKERHUB_TOKEN="${CONF_DOCKERHUB_TOKEN:-}"
  DOCKERHUB_NAMESPACE="${CONF_DOCKERHUB_NAMESPACE:-}"
  local var_imagem="CONF_$IMAGEM_CHAVE"
  NOME_IMAGEM="${!var_imagem:-}"

  [ -n "$DOCKERHUB_USERNAME" ] || erro "DOCKERHUB_USERNAME vazio em $CONF."
  [ -n "$DOCKERHUB_TOKEN" ] || erro "DOCKERHUB_TOKEN vazio em $CONF."
  [ -n "$NOME_IMAGEM" ] || erro "$IMAGEM_CHAVE vazio em $CONF."
  [ -n "$DOCKERHUB_NAMESPACE" ] || DOCKERHUB_NAMESPACE="$DOCKERHUB_USERNAME"
}

# proxima_versao <versao> <major|minor|patch>
proxima_versao() {
  local versao="$1" incremento="$2" major minor patch
  [[ "$versao" =~ ^([0-9]+)\.([0-9]+)\.([0-9]+)$ ]] || erro "versão '$versao' não está no formato X.Y.Z."
  major="${BASH_REMATCH[1]}"; minor="${BASH_REMATCH[2]}"; patch="${BASH_REMATCH[3]}"
  case "$incremento" in
    major) echo "$((major + 1)).0.0" ;;
    minor) echo "$major.$((minor + 1)).0" ;;
    patch) echo "$major.$minor.$((patch + 1))" ;;
  esac
}

# Insere a entrada (arquivo temporário) antes da primeira seção "## " do CHANGELOG.
registrar_changelog() {
  local entrada="$1" tmp
  if [ ! -f "$CHANGELOG" ]; then
    printf '# Histórico de versões\n\nImagens publicadas no Docker Hub, da mais recente para a mais antiga. Gerado pelos scripts em `scripts/docker/`.\n\n' > "$CHANGELOG"
  fi
  tmp="$(mktemp)"
  awk -v arq="$entrada" '
    !feito && /^## / { while ((getline l < arq) > 0) print l; print ""; feito = 1 }
    { print }
    END { if (!feito) { while ((getline l < arq) > 0) print l } }
  ' "$CHANGELOG" > "$tmp"
  mv "$tmp" "$CHANGELOG"
}

# publicar <major|minor|patch>
publicar() {
  local incremento="${1:-patch}"
  case "$incremento" in
    major|minor|patch) ;;
    *) erro "incremento inválido '$incremento'. Use major, minor ou patch." ;;
  esac

  exigir_comando git
  exigir_comando docker
  cd "$RAIZ"
  carregar_conf

  [ -z "$(git status --porcelain)" ] || erro "há alterações não commitadas. Faça commit ou stash antes de publicar."

  local versao nova tag repo commit commit_curto tag_anterior mudancas entrada
  versao="$(ler_versao)"
  [ -n "$versao" ] || erro "não foi possível ler a versão de $PROJETO."
  nova="$(proxima_versao "$versao" "$incremento")"
  tag="$PROJETO-v$versao"
  repo="$DOCKERHUB_NAMESPACE/$NOME_IMAGEM"
  commit="$(git rev-parse HEAD)"
  commit_curto="$(git rev-parse --short HEAD)"

  [ -z "$(git tag --list "$tag")" ] || erro "a tag git $tag já existe. A versão $versao já foi publicada?"

  # Mudanças desde a última publicação do projeto, sem os próprios commits de publicação.
  tag_anterior="$(git tag --list "$PROJETO-v*" --sort=-v:refname | head -n 1)"
  local intervalo=(-n 20)
  [ -z "$tag_anterior" ] || intervalo=("$tag_anterior..HEAD")
  mudancas="$(git log --oneline --invert-grep --grep="^chore($PROJETO): publicar" "${intervalo[@]}" -- "$PROJETO/" | sed 's/^/  - /')"
  [ -n "$mudancas" ] || mudancas="  - (sem commits em $PROJETO/)"

  info "Construindo $repo:$versao (commit $commit_curto)"
  docker build \
    -t "$repo:$versao" \
    -t "$repo:latest" \
    --label "org.opencontainers.image.title=$NOME_IMAGEM" \
    --label "org.opencontainers.image.version=$versao" \
    --label "org.opencontainers.image.revision=$commit" \
    --label "org.opencontainers.image.created=$(date -u +%Y-%m-%dT%H:%M:%SZ)" \
    "$RAIZ/$PROJETO"

  info "Publicando no Docker Hub"
  printf '%s' "$DOCKERHUB_TOKEN" | docker login -u "$DOCKERHUB_USERNAME" --password-stdin
  docker push "$repo:$versao"
  docker push "$repo:latest"

  info "Criando tag git $tag"
  git tag -a "$tag" -m "$PROJETO $versao publicado como $repo:$versao"

  info "Avançando versão de $PROJETO: $versao -> $nova"
  gravar_versao "$nova"

  entrada="$(mktemp)"
  {
    echo "## $PROJETO $versao — $(date +%Y-%m-%d)"
    echo
    echo "- Imagem: \`$repo:$versao\` (também \`latest\`)"
    echo "- Commit: \`$commit_curto\` (tag \`$tag\`)"
    echo "- Próxima versão de desenvolvimento: $nova"
    echo "- Mudanças:"
    echo "$mudancas"
  } > "$entrada"
  registrar_changelog "$entrada"
  rm -f "$entrada"

  git add "${ARQUIVOS_VERSAO[@]}" CHANGELOG.md
  git commit -m "chore($PROJETO): publicar $versao e avançar para $nova"

  info "Concluído: $repo:$versao publicado; $PROJETO agora em $nova."
  echo "    Envie o commit e a tag com: git push --follow-tags"
}
