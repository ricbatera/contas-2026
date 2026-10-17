---
name: status-servidor
description: Inspeciona, sem alterar nada, o ambiente implantado no servidor via SSH - estado e saúde dos containers, imagens e versões em uso, logs do backend/frontend/banco e uso de disco. Use para diagnosticar falhas de deploy ou quando perguntarem "o que está rodando no servidor", "por que o deploy falhou" ou "mostre os logs de produção".
argument-hint: "[status|logs <serviço>|disco]"
---

# Status do servidor

Somente leitura. Esta skill não para, reinicia nem remove nada. Se o diagnóstico indicar que é preciso agir (restart, limpeza, novo deploy), proponha ao usuário e espere confirmação.

## Conexão
Leia de `scripts/deploy/deploy.conf` só `SSH_HOST`, `SSH_PORT`, `SSH_USER`, `SSH_KEY` e `REMOTE_PATH`. Nunca exiba `SSH_PASSWORD`.

- Com chave (Windows ou Linux):
  ```powershell
  ssh -i <SSH_KEY> -p <SSH_PORT> -o StrictHostKeyChecking=accept-new <SSH_USER>@<SSH_HOST> "<comando>"
  ```
- Só com senha no Windows: `plink -ssh -P <porta> -pw <senha> usuario@host "<comando>"`, se o plink existir. Passe a senha direto do conf, sem ecoá-la. Sem plink, peça ao usuário para rodar o comando com `!` ou para configurar uma chave.
- Só com senha no Linux: `SSHPASS=... sshpass -e ssh ...`.

Todos os comandos rodam em `REMOTE_PATH`. Prefixe com `cd <REMOTE_PATH> &&` e use `docker compose --env-file .env-prod`.

## Comandos
| Objetivo | Comando remoto |
|---|---|
| Containers e saúde | `docker compose --env-file .env-prod ps` |
| Imagens em uso | `grep 'image:' docker-compose.yml` |
| Logs recentes | `docker compose --env-file .env-prod logs --tail 200 <db\|backend\|frontend>` |
| Health do backend | `docker compose --env-file .env-prod exec -T backend wget -qO- http://localhost:8080/actuator/health` |
| Disco e imagens | `df -h / && docker system df && docker images` |
| Front acessível | local: `curl.exe -s -o NUL -w "%{http_code}" http://<SSH_HOST>:18050/` |

Nunca rode `cat .env-prod`, `docker compose config` sem filtro (mostra as variáveis resolvidas, com senhas) nem `docker inspect` de container com env. Para checar uma variável, verifique só se ela existe.

## Diagnóstico comum
- backend `unhealthy` ou reiniciando: veja `logs backend`. Erro de conexão com `db`: banco não subiu ou credenciais divergentes no `.env-prod`. `jwt.secret deve ter no mínimo 32 bytes`: `JWT_SECRET` curto demais. `FlywayException`: migration com problema.
- frontend sem resposta em 18050: container parado, porta ocupada por outro processo (`ss -ltnp | grep 18050`) ou firewall (`sudo ufw status`, que exige sudo; peça ao usuário).
- disco cheio: proponha `docker image prune` (imagens sem uso) ao usuário. Nunca `docker volume prune` nem `down -v`.

## Resultado
Resuma: containers e estado, imagens e versões em uso, e problemas encontrados com a evidência (trecho do log) e a ação sugerida.
