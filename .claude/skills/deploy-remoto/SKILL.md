---
name: deploy-remoto
description: Faz o deploy (ou rollback) das imagens do backend e do frontend num servidor Ubuntu via SSH usando scripts/deploy - envia o compose de deploy (front na porta 18050) e o .env-prod, baixa o ambiente antigo preservando o banco, remove as imagens antigas e sobe as novas. Use quando pedirem "deploy", "subir no servidor", "atualizar produção" ou "voltar para a versão X".
argument-hint: "[versão-backend] [versão-frontend]"
---

# Deploy remoto via SSH

Argumentos opcionais: versão do backend e do frontend (ex.: `1.0.3 1.0.2`). Sem argumentos, use `latest` nos dois. Para rollback, o usuário informa a versão anterior. Consulte o `CHANGELOG.md` para ver as versões publicadas.

## 1. Pré-condições
1. `scripts/deploy/deploy.conf` existe. Se não existir, peça ao usuário para copiar `deploy.conf.example` e preencher. Não exiba o conteúdo; leia só `SSH_HOST`, `SSH_PORT`, `SSH_USER`, `REMOTE_PATH` e `DOCKERHUB_NAMESPACE`/`*_IMAGE` (por exemplo, com `Select-String` filtrando essas chaves).
2. O arquivo do `ENV_FILE` (padrão `.env-prod` na raiz) existe. Nunca exiba o conteúdo dele.
3. Autenticação: o script interativo precisa rodar sem prompt de senha.
   - `SSH_KEY` preenchido: ok.
   - Só `SSH_PASSWORD` no Windows: precisa de `plink` e `pscp` no PATH (`Get-Command plink`). Sem eles, o ssh pediria a senha no console e a execução travaria: peça ao usuário para usar chave ou instalar o PuTTY (`winget install PuTTY.PuTTY`), ou para ele mesmo rodar o script com `! .\scripts\deploy\deploy.ps1`.
4. As versões pedidas existem no Docker Hub: `wsl docker manifest inspect <namespace>/<imagem>:<tag>` (código de saída 0 = existe). Se não existir, avise. Talvez falte publicar (skill `publicar-imagem`).

## 2. Confirmação
Mostre e espere aprovação explícita: servidor (`usuario@host:porta`), pasta remota, imagem completa do backend e do frontend, e o aviso de que o ambiente atual será parado (indisponibilidade de alguns segundos a poucos minutos). Os dados do banco são preservados.

## 3. Execução
O script pergunta, nesta ordem: imagem do backend, imagem do frontend e confirmação `s/N`. Responda pelo stdin. Linha vazia = `latest`; só a tag completa o repositório.

Windows (timeout 600000 ms):
```powershell
"1.0.3`n1.0.2`ns" | powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\deploy\deploy.ps1
```
Linux:
```bash
printf '1.0.3\n1.0.2\ns\n' | ./scripts/deploy/deploy.sh
```
Para `latest` nos dois: `"`n`ns"` (PowerShell) ou `printf '\n\ns\n'` (bash).

O deploy termina com sucesso só quando `docker compose up --wait` vê os serviços saudáveis. Em falha:
- `docker ou docker compose não disponível`: o usuário SSH não está no grupo `docker` ou falta o plugin compose no servidor.
- falha no `pull`: imagem ou tag inexistente, ou falta de rede no servidor.
- timeout do `--wait`: algum serviço não ficou saudável. Use a skill `status-servidor` para ver os logs (backend sem conexão com o banco, `JWT_SECRET` curto demais e migration Flyway com erro são as causas comuns).

## 4. Verificação
1. `curl -s -o /dev/null -w '%{http_code}' http://<SSH_HOST>:18050/` deve retornar 200 (no Windows: `curl.exe`).
2. Estado dos containers e imagens em uso: skill `status-servidor`.
3. Relate as imagens no ar, o resultado da verificação e a URL `http://<SSH_HOST>:18050`.

## Rollback
É um deploy com as versões anteriores (veja o `CHANGELOG.md`). O banco não volta: se a versão nova aplicou migrations Flyway, a versão antiga pode não ser compatível com o schema. Verifique no `CHANGELOG.md`/`git log` se houve migration nova (`backend/src/main/resources/db/migration`) entre as versões e avise o usuário antes de continuar.
