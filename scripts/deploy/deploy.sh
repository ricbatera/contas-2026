#!/usr/bin/env bash
# Deploy remoto via SSH: prepara a pasta no servidor, envia o compose de deploy (front na
# porta 18050) com as imagens escolhidas e o .env-prod, baixa o ambiente antigo, remove as
# imagens antigas e sobe o ambiente novamente.
# Uso: ./scripts/deploy/deploy.sh   (configuração em scripts/deploy/deploy.conf)

set -euo pipefail

RAIZ="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
DIR_DEPLOY="$RAIZ/scripts/deploy"
CONF="$DIR_DEPLOY/deploy.conf"
REGEX_IMAGEM='^([a-z0-9.-]+(:[0-9]+)?/)?[a-z0-9]([a-z0-9._/-]*[a-z0-9])?(:[A-Za-z0-9._-]+)?$'

erro() {
  echo "ERRO: $*" >&2
  exit 1
}

info() {
  echo "==> $*"
}

exigir_comando() {
  command -v "$1" >/dev/null 2>&1 || erro "comando '$1' não encontrado no PATH.${2:+ $2}"
}

# Lê o conf linha a linha (sem source, para não executar nada do arquivo).
carregar_conf() {
  [ -f "$CONF" ] || erro "arquivo $CONF não encontrado. Copie deploy.conf.example para deploy.conf e preencha."
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

  SSH_HOST="${CONF_SSH_HOST:-}"
  SSH_PORT="${CONF_SSH_PORT:-22}"
  SSH_USER="${CONF_SSH_USER:-}"
  SSH_KEY="${CONF_SSH_KEY:-}"
  SSH_PASSWORD="${CONF_SSH_PASSWORD:-}"
  REMOTE_PATH="${CONF_REMOTE_PATH:-}"
  ENV_FILE="${CONF_ENV_FILE:-.env-prod}"
  DOCKERHUB_NAMESPACE="${CONF_DOCKERHUB_NAMESPACE:-}"
  BACKEND_IMAGE="${CONF_BACKEND_IMAGE:-financas-backend}"
  FRONTEND_IMAGE="${CONF_FRONTEND_IMAGE:-financas-frontend}"

  [ -n "$SSH_HOST" ] || erro "SSH_HOST vazio em $CONF."
  [ -n "$SSH_USER" ] || erro "SSH_USER vazio em $CONF."
  [[ "$SSH_PORT" =~ ^[0-9]+$ ]] || erro "SSH_PORT inválida em $CONF."
  [ -n "$SSH_KEY$SSH_PASSWORD" ] || erro "preencha SSH_KEY ou SSH_PASSWORD em $CONF."
  [[ "$REMOTE_PATH" =~ ^/[A-Za-z0-9._/-]+$ ]] || erro "REMOTE_PATH deve ser um caminho absoluto sem espaços (ex.: /opt/financas)."
  [ -n "$DOCKERHUB_NAMESPACE" ] || erro "DOCKERHUB_NAMESPACE vazio em $CONF."
  REMOTE_PATH="${REMOTE_PATH%/}"
  ENV_LOCAL="$RAIZ/$ENV_FILE"
  [ -f "$ENV_LOCAL" ] || erro "arquivo de ambiente $ENV_LOCAL não encontrado (ENV_FILE em $CONF)."
}

configurar_ssh() {
  exigir_comando ssh
  exigir_comando scp
  DESTINO="$SSH_USER@$SSH_HOST"
  local opcoes=(-o StrictHostKeyChecking=accept-new)
  if [ -n "$SSH_KEY" ]; then
    SSH_KEY="${SSH_KEY/#\~/$HOME}"
    [ -f "$SSH_KEY" ] || erro "chave SSH $SSH_KEY não encontrada."
    SSH_CMD=(ssh -i "$SSH_KEY" -p "$SSH_PORT" "${opcoes[@]}")
    SCP_CMD=(scp -i "$SSH_KEY" -P "$SSH_PORT" "${opcoes[@]}")
  else
    exigir_comando sshpass "Instale com: sudo apt install sshpass (ou use SSH_KEY)."
    # Senha pela variável SSHPASS, para não aparecer na lista de processos.
    export SSHPASS="$SSH_PASSWORD"
    opcoes+=(-o PreferredAuthentications=password,keyboard-interactive -o PubkeyAuthentication=no)
    SSH_CMD=(sshpass -e ssh -p "$SSH_PORT" "${opcoes[@]}")
    SCP_CMD=(sshpass -e scp -P "$SSH_PORT" "${opcoes[@]}")
  fi
}

remoto() {
  "${SSH_CMD[@]}" "$DESTINO" "$1"
}

# perguntar_imagem <rótulo> <repositório padrão>: Enter aceita :latest; só uma tag completa o repositório.
perguntar_imagem() {
  local rotulo="$1" repo="$2" padrao resposta
  padrao="$repo:latest"
  read -r -p "Imagem do $rotulo [$padrao]: " resposta
  resposta="$(echo "$resposta" | xargs)"
  if [ -z "$resposta" ]; then
    resposta="$padrao"
  elif [[ "$resposta" != */* && "$resposta" != *:* ]]; then
    resposta="$repo:$resposta"
  fi
  [[ "$resposta" =~ $REGEX_IMAGEM ]] || erro "nome de imagem inválido: $resposta"
  echo "$resposta"
}

carregar_conf
configurar_ssh

IMAGEM_BACKEND="$(perguntar_imagem backend "$DOCKERHUB_NAMESPACE/$BACKEND_IMAGE")"
IMAGEM_FRONTEND="$(perguntar_imagem frontend "$DOCKERHUB_NAMESPACE/$FRONTEND_IMAGE")"

echo
echo "Servidor:  $DESTINO (porta $SSH_PORT)"
echo "Pasta:     $REMOTE_PATH"
echo "Backend:   $IMAGEM_BACKEND"
echo "Frontend:  $IMAGEM_FRONTEND"
echo "Ambiente:  $ENV_FILE"
read -r -p "Confirma o deploy? [s/N] " confirma
[[ "$confirma" =~ ^[sS]$ ]] || { echo "Deploy cancelado."; exit 0; }

info "Verificando a pasta $REMOTE_PATH no servidor"
situacao="$(remoto "command -v docker >/dev/null && docker compose version >/dev/null || { echo SEM_DOCKER; exit 0; }; if [ -d '$REMOTE_PATH' ]; then echo EXISTE; else mkdir -p '$REMOTE_PATH' && echo CRIADO; fi")"
case "$situacao" in
  *SEM_DOCKER*) erro "docker ou docker compose não disponível para $SSH_USER no servidor." ;;
  *EXISTE*) info "Pasta existe: o ambiente será atualizado." ;;
  *CRIADO*) info "Pasta criada: primeira instalação." ;;
  *) erro "resposta inesperada do servidor: $situacao" ;;
esac

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
sed -e 's/\r$//' \
    -e "s|__BACKEND_IMAGE__|$IMAGEM_BACKEND|" \
    -e "s|__FRONTEND_IMAGE__|$IMAGEM_FRONTEND|" \
    "$DIR_DEPLOY/docker-compose.deploy.yml" > "$TMP/docker-compose.yml.novo"
sed -e 's/\r$//' -e '1s/^\xEF\xBB\xBF//' "$ENV_LOCAL" > "$TMP/.env-prod.novo"
sed -e 's/\r$//' "$DIR_DEPLOY/remoto.sh" > "$TMP/.deploy-remoto.sh"

info "Enviando compose, $ENV_FILE e script de deploy"
"${SCP_CMD[@]}" "$TMP/docker-compose.yml.novo" "$TMP/.env-prod.novo" "$TMP/.deploy-remoto.sh" "$DESTINO:$REMOTE_PATH/"

info "Executando o deploy no servidor"
remoto "bash '$REMOTE_PATH/.deploy-remoto.sh' '$REMOTE_PATH'"

info "Concluído. Aplicação em http://$SSH_HOST:18050"
