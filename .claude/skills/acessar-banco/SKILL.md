---
name: acessar-banco
description: Acessa o PostgreSQL do projeto - o banco local (container postgres-financas no WSL, bancos financas e financas_test) ou o de produção (serviço db do docker compose no servidor, via SSH) - para consultas, execução de scripts SQL, backup (pg_dump) e restauração (pg_restore) usando scripts/db/banco.sh. Use quando pedirem para "consultar o banco", "rodar um SQL", "ver os dados de produção", "fazer backup do banco", "copiar o banco do servidor" ou "restaurar um dump".
argument-hint: "[local|teste|remoto] [consulta ou tarefa]"
---

# Acessar banco

Todo acesso passa por `scripts/db/banco.sh`, rodado com a ferramenta **Bash** (Git Bash), não PowerShell: o PowerShell 5.1 corrompe binários em redirecionamento (`>`), o que estraga dumps.

```bash
./scripts/db/banco.sh <alvo> <psql|pg_dump|pg_restore> [argumentos...]
```

| Alvo | Onde | Banco / usuário |
|---|---|---|
| `local` | container `postgres-financas` no WSL | `financas` / `postgres` |
| `teste` | mesmo container | `financas_test` / `postgres` (recriado pelos testes, não guarde nada nele) |
| `remoto` | serviço `db` do compose em `REMOTE_PATH` no servidor | `POSTGRES_DB` / `POSTGRES_USER` do próprio container |

O script injeta `-U` e `-d`; não os repita. stdin e stdout passam direto, então SQL pode vir de arquivo ou heredoc e dumps vão para arquivo local. No remoto, a conexão usa `scripts/deploy/deploy.conf` (host, porta, usuário, chave ou senha, pasta) e roda em modo batch: se falhar com código 255, a autenticação não está configurada (veja "Conexão remota").

## Exemplos
```bash
# Consulta
./scripts/db/banco.sh local psql -c "select id, descricao from saida order by created_at desc limit 10"

# Saída fácil de processar: -At (sem alinhamento e cabeçalho), -F para o separador
./scripts/db/banco.sh remoto psql -At -F ';' -c "select tenant_id, count(*) from saida group by 1"

# Script SQL numa transação, parando no primeiro erro
./scripts/db/banco.sh remoto psql -v ON_ERROR_STOP=1 -1 < caminho/script.sql

# SQL inline por heredoc (aspas simples no delimitador: sem expansão do shell)
./scripts/db/banco.sh local psql -v ON_ERROR_STOP=1 <<'SQL'
select version, description, success from flyway_schema_history order by installed_rank;
SQL

# Estrutura
./scripts/db/banco.sh local psql -c '\dt'
./scripts/db/banco.sh local psql -c '\d+ saida'

# Backup em formato custom (comprimido, aceita restauração seletiva)
./scripts/db/banco.sh remoto pg_dump -Fc > backups/financas-prod-$(date +%Y%m%d-%H%M%S).dump

# Copiar produção para o banco local (só dados de negócio; schema já vem do Flyway)
./scripts/db/banco.sh local pg_restore --clean --if-exists --no-owner --no-privileges < backups/arquivo.dump
```

Grave backups em `backups/` na raiz (crie a pasta se não existir; ela é ignorada pelo git, pois dumps contêm dados pessoais) ou no scratchpad da sessão. Nunca versione um dump. Depois de um `pg_dump`, confira o arquivo com `pg_restore -l < arquivo | head`: tamanho zero ou erro indica dump inválido.

## Regras de segurança
1. **Leitura é livre; escrita é com confirmação.** `select`, `\d`, `explain` e `pg_dump` podem rodar direto. Qualquer `insert`, `update`, `delete`, `truncate`, DDL, `pg_restore` ou `psql -f` com escrita exige "sim" do usuário, ainda mais no alvo `remoto`. Antes de pedir, mostre o SQL e o efeito esperado (ex.: quantas linhas um `select count(*)` com o mesmo `where` encontra).
2. **Backup antes de escrever em produção.** Antes de qualquer escrita no `remoto`, faça um `pg_dump -Fc` e confirme que é válido. Informe o caminho do arquivo ao usuário.
3. **Transação e parada no erro.** Scripts de escrita rodam com `-v ON_ERROR_STOP=1 -1`. Para validar sem gravar, rode o script entre `begin;` e `rollback;` e mostre o resultado.
4. **Nunca restaure sobre produção** (`remoto pg_restore`, `--clean`, `drop`) sem pedido explícito do usuário para exatamente isso, com o backup recém-feito.
5. **Respeite o tenant.** Toda tabela de negócio tem `tenant_id` (id do usuário em `usuario`). Fora da aplicação, o filtro Hibernate não existe: inclua `tenant_id` nos `where` e nos `insert` de dados de um usuário.
6. **Schema é do Flyway.** Mudança de estrutura vira migration nova em `backend/src/main/resources/db/migration` (`V{n}__descricao.sql`), nunca DDL manual no banco nem edição de migration aplicada. DDL direto só para diagnóstico em `local`/`teste`, e avise que diverge do Flyway.
7. **Não exiba segredos**: não leia `.env-prod` nem imprima `SSH_PASSWORD` ou senhas de `usuario.senha`/`refresh_token`. Em consultas a `usuario`, selecione só colunas necessárias.

## Conexão remota
Ordem usada pelo script: `SSH_KEY` do conf; senha com `sshpass` (Linux) ou `plink` (Windows, PuTTY); senão, chaves padrão do `~/.ssh` em modo batch. Se a conexão falhar, não tente adivinhar credenciais. Peça ao usuário para configurar uma chave:

```
! ssh-keygen -t ed25519 -f ~/.ssh/financas_deploy -N ""
! ssh-copy-id -i ~/.ssh/financas_deploy.pub -p <porta> <usuario>@<host>
```

(no Windows sem `ssh-copy-id`: `type $env:USERPROFILE\.ssh\financas_deploy.pub | ssh -p <porta> <usuario>@<host> "mkdir -p ~/.ssh && cat >> ~/.ssh/authorized_keys"`), e então preencher `SSH_KEY=~/.ssh/financas_deploy` no `deploy.conf`.

## Problemas comuns
- `Error response from daemon: No such container: postgres-financas`: o container local está parado. Suba com `wsl docker start postgres-financas`.
- `service "db" is not running` no remoto: o ambiente está fora do ar; use a skill `status-servidor`.
- `pg_restore: error: unsupported version`: o `pg_dump` do container de origem é mais novo que o do destino. Gere o dump no formato plano (`pg_dump` sem `-Fc`) ou alinhe as versões.
- Caracteres acentuados quebrados ou erro de sintaxe na primeira linha de um script: salve o arquivo SQL em UTF-8 sem BOM (o `Out-File` do PowerShell 5.1 grava com BOM; use a ferramenta Write).
