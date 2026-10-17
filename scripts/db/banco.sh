#!/usr/bin/env bash
# Executa psql, pg_dump ou pg_restore dentro do container PostgreSQL do ambiente escolhido.
# Funciona no Git Bash (Windows) e no Linux. Entrada e saída padrão passam direto para a
# ferramenta, então dá para enviar SQL por stdin e gravar dumps em arquivo local.
#
# Uso: ./scripts/db/banco.sh <alvo> <psql|pg_dump|pg_restore> [argumentos...]
#   alvo local   container postgres-financas do WSL, banco financas
#   alvo teste   container postgres-financas do WSL, banco financas_test
#   alvo remoto  serviço db do docker compose no servidor (scripts/deploy/deploy.conf)
#
# Usuário e banco são passados pelo script (-U/-d). Exemplos:
#   ./scripts/db/banco.sh local psql -c 'select count(*) from saida'
#   ./scripts/db/banco.sh remoto psql -v ON_ERROR_STOP=1 -1 < script.sql
#   ./scripts/db/banco.sh remoto pg_dump -Fc > backup.dump
#   ./scripts/db/banco.sh local pg_restore --clean --if-exists --no-owner < backup.dump

set -euo pipefail

# No Git Bash, impede a conversão de argumentos como /opt/... em caminhos do Windows.
export MSYS_NO_PATHCONV=1
# PuTTY (plink) instalado na pasta padrão do Windows, mesmo fora do PATH.
[ -d "/c/Program Files/PuTTY" ] && PATH="$PATH:/c/Program Files/PuTTY"

RAIZ="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
CONF="$RAIZ/scripts/deploy/deploy.conf"
CONTAINER_LOCAL="postgres-financas"
USUARIO_LOCAL="postgres"

erro() {
  echo "ERRO: $*" >&2
  exit 1
}

uso() {
  sed -n '5,16p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//' >&2
  exit 1
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

  [ -n "$SSH_HOST" ] || erro "SSH_HOST vazio em $CONF."
  [ -n "$SSH_USER" ] || erro "SSH_USER vazio em $CONF."
  [[ "$SSH_PORT" =~ ^[0-9]+$ ]] || erro "SSH_PORT inválida em $CONF."
  [[ "$REMOTE_PATH" =~ ^/[A-Za-z0-9._/-]+$ ]] || erro "REMOTE_PATH deve ser um caminho absoluto sem espaços (ex.: /opt/financas)."
  REMOTE_PATH="${REMOTE_PATH%/}"
}

# Monta SSH_CMD: chave do conf, senha (sshpass ou plink) ou, sem nada disso, as chaves
# padrão do ~/.ssh em modo batch (falha em vez de pedir senha).
configurar_ssh() {
  local destino="$SSH_USER@$SSH_HOST"
  local opcoes=(-o StrictHostKeyChecking=accept-new)
  if [ -n "$SSH_KEY" ]; then
    SSH_KEY="${SSH_KEY/#\~/$HOME}"
    [ -f "$SSH_KEY" ] || erro "chave SSH $SSH_KEY não encontrada."
    SSH_CMD=(ssh -i "$SSH_KEY" -p "$SSH_PORT" "${opcoes[@]}" -o BatchMode=yes "$destino")
  elif [ -n "$SSH_PASSWORD" ] && command -v sshpass >/dev/null 2>&1; then
    # Senha pela variável SSHPASS, para não aparecer na lista de processos.
    export SSHPASS="$SSH_PASSWORD"
    opcoes+=(-o PreferredAuthentications=password,keyboard-interactive -o PubkeyAuthentication=no)
    SSH_CMD=(sshpass -e ssh -p "$SSH_PORT" "${opcoes[@]}" "$destino")
  elif [ -n "$SSH_PASSWORD" ] && command -v plink >/dev/null 2>&1; then
    # Em modo batch o plink não confirma chave nova: aceita as que o OpenSSH já conhece.
    local alvo="$SSH_HOST" hostkeys=() chave
    [ "$SSH_PORT" = 22 ] || alvo="[$SSH_HOST]:$SSH_PORT"
    while read -r chave; do
      hostkeys+=(-hostkey "$chave")
    done < <(ssh-keygen -l -F "$alvo" 2>/dev/null | grep -o 'SHA256:[^ ]*' || true)
    [ ${#hostkeys[@]} -gt 0 ] || erro "chave do servidor $alvo não está em known_hosts. Conecte uma vez com: ssh -p $SSH_PORT $destino"
    SSH_CMD=(plink -ssh -batch -P "$SSH_PORT" -pw "$SSH_PASSWORD" "${hostkeys[@]}" "$destino")
  else
    command -v ssh >/dev/null 2>&1 || erro "comando 'ssh' não encontrado no PATH."
    SSH_CMD=(ssh -p "$SSH_PORT" "${opcoes[@]}" -o BatchMode=yes "$destino")
  fi
}

[ $# -ge 2 ] || uso
ALVO="$1"
FERRAMENTA="$2"
shift 2

case "$FERRAMENTA" in
  psql|pg_dump|pg_restore) ;;
  *) erro "ferramenta '$FERRAMENTA' não suportada (use psql, pg_dump ou pg_restore)." ;;
esac

case "$ALVO" in
  local|teste)
    banco="financas"
    [ "$ALVO" = teste ] && banco="financas_test"
    command -v wsl >/dev/null 2>&1 && DOCKER=(wsl docker) || DOCKER=(docker)
    exec "${DOCKER[@]}" exec -i "$CONTAINER_LOCAL" "$FERRAMENTA" -U "$USUARIO_LOCAL" -d "$banco" "$@"
    ;;
  remoto)
    carregar_conf
    configurar_ssh
    # Usuário e banco vêm das variáveis do próprio container, sem ler o .env-prod.
    comando_remoto="cd $(printf '%q' "$REMOTE_PATH") && docker compose --env-file .env-prod exec -T db"
    comando_remoto+=" sh -c 'exec \"\$0\" -U \"\$POSTGRES_USER\" -d \"\$POSTGRES_DB\" \"\$@\"' $FERRAMENTA"
    for argumento in "$@"; do
      comando_remoto+=" $(printf '%q' "$argumento")"
    done
    status=0
    "${SSH_CMD[@]}" "$comando_remoto" || status=$?
    if [ "$status" -eq 255 ]; then
      echo "ERRO: falha na conexão SSH. Configure SSH_KEY em $CONF (ssh-copy-id) ou instale sshpass/plink para usar SSH_PASSWORD." >&2
    fi
    exit "$status"
    ;;
  *)
    uso
    ;;
esac
