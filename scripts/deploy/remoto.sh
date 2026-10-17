#!/usr/bin/env bash
# Executado NO SERVIDOR pelos scripts deploy.sh/deploy.ps1, que o enviam junto com
# docker-compose.yml.novo e .env-prod.novo. Não execute localmente.
# Uso: bash .deploy-remoto.sh <pasta-de-deploy>

set -euo pipefail

PASTA="$1"
cd "$PASTA"

info() {
  echo "[servidor] $*"
}

compose() {
  docker compose --env-file .env-prod "$@"
}

[ -f docker-compose.yml.novo ] || { echo "[servidor] ERRO: docker-compose.yml.novo não encontrado em $PASTA." >&2; exit 1; }
[ -f .env-prod.novo ] || { echo "[servidor] ERRO: .env-prod.novo não encontrado em $PASTA." >&2; exit 1; }

if [ -f docker-compose.yml ]; then
  info "Ambiente existente encontrado; baixando."
  # Imagens da aplicação no compose antigo, por nome:tag (por ID, o rm apagaria também
  # outras tags da mesma imagem). A do postgres fica, pois o banco continua igual.
  imagens_antigas="$(compose config --images | grep -v '^postgres:' | sort -u || true)"
  # Sem -v: o volume pgdata (dados do banco) é preservado.
  compose down --remove-orphans
  if [ -n "$imagens_antigas" ]; then
    info "Removendo imagens antigas da aplicação."
    for imagem in $imagens_antigas; do
      docker image rm "$imagem" || info "Imagem $imagem não removida (em uso por outro container?)."
    done
  fi
else
  info "Primeira instalação nesta pasta."
fi

mv -f docker-compose.yml.novo docker-compose.yml
mv -f .env-prod.novo .env-prod
chmod 600 .env-prod

info "Baixando as imagens."
compose pull

info "Subindo o ambiente (aguardando os serviços ficarem saudáveis)."
compose up -d --wait --wait-timeout 300

compose ps
rm -f "$0"
info "Deploy concluído."
