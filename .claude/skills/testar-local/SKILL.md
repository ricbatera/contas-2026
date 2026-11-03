---
name: testar-local
description: Sobe o ambiente local para o usuário testar no navegador e deixa os logs ao alcance do Claude - confere o PostgreSQL do WSL (container postgres-financas, bancos financas e financas_test), inicia o backend (perfil local, :8080) e o frontend (ng serve, :4200) em segundo plano gravando a saída em logs/local/, e depois lê esses logs e o banco para diagnosticar erros. Use quando o usuário disser "vamos testar", "sobe o ambiente", "sobe o back e o front", "reinicia o backend" ou pedir para ver os logs de um teste local.
---

# Testar localmente com logs

O objetivo é que o próprio Claude rode backend e frontend, para conseguir ler os logs quando o usuário relatar um erro. Processos abertos pelo usuário em outros terminais não deixam log acessível, e o backend não grava log em arquivo.

Logs:
- `logs/local/backend.log`: saída do `spring-boot:run`;
- `logs/local/frontend.log`: saída do `ng serve`.

Estão ignorados pelo git (`*.log`). Rode tudo a partir da raiz do repositório (`C:\projetos\minhasContas\2026-Novo`), pelo Bash.

## 1. Banco no WSL

```bash
wsl docker ps -a --filter name=postgres-financas --format '{{.Status}}'
```
- Se não estiver `Up`, rode `wsl docker start postgres-financas` e espere `wsl docker exec postgres-financas pg_isready -U postgres` responder `accepting connections`.
- O perfil `local` usa o banco `financas`; os testes automatizados (`mvnw verify`) usam o `financas_test`, no mesmo container.

## 2. Portas ocupadas

```bash
powershell -NoProfile -Command "Get-NetTCPConnection -State Listen -LocalPort 8080,4200 -ErrorAction SilentlyContinue | Select-Object LocalPort,OwningProcess"
```
Se a porta estiver ocupada por um processo que **não** foi iniciado por você nesta sessão, provavelmente é do usuário em outro terminal. Pergunte antes de encerrar, explicando que, sem isso, você não consegue ler os logs daquela aplicação. Com autorização, encerre pelo PID:
`powershell -NoProfile -Command "Stop-Process -Id <PID>"`.

## 3. Subir backend e frontend

Crie a pasta e zere os logs antigos: `mkdir -p logs/local`.

Backend, com o Bash e `run_in_background: true`:
```bash
cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=local > ../logs/local/backend.log 2>&1
```

Frontend, com o Bash e `run_in_background: true`:
```bash
cd frontend && npm start > ../logs/local/frontend.log 2>&1
```

Espere ficarem prontos com o Monitor (ou um loop `until` com limite de ~180 s), sem `sleep` solto:
- backend pronto: `curl -s -o /dev/null -w '%{http_code}' http://localhost:8080/actuator/health` devolve `200`;
- frontend pronto: `curl -s -o /dev/null -w '%{http_code}' http://localhost:4200/` devolve `200`.

Se um deles cair antes, leia o fim do log (`tail -n 80 logs/local/backend.log`). Causas comuns:
- `ClassFormatError`: classes corrompidas em `target/`. Rode `./mvnw clean` e suba de novo;
- falha do Flyway ou da validação do Hibernate: migration nova com erro, então mostre a mensagem;
- `Connection refused` na porta 5432: volte à etapa 1.

Avise o usuário que está pronto em http://localhost:4200, com o proxy de `/api` para o backend.

## 4. Quando o usuário relatar um erro

1. Procure no log do backend em volta do horário do teste: `grep -n -E "ERROR|WARN|Exception" logs/local/backend.log | tail -n 40`. Depois leia o trecho com `sed -n '<ini>,<fim>p'`.
2. Para erros de tela ou de compilação do Angular, veja `logs/local/frontend.log`.
3. Complete com o estado no banco local, por exemplo:
   `wsl docker exec postgres-financas psql -U postgres -d financas -c "<SQL>"`.
   Na importação de fatura, a tabela `fatura_importacao` guarda `status`, `erro` e `resposta_ia` (as respostas da IA, inclusive na falha).
4. Não mostre segredos que apareçam no log (tokens, chaves de API, senhas). Mascare-os (`abc****`).

## 5. Depois de mudar código

**Backend:** o projeto tem `spring-boot-devtools`, que reinicia o contexto sozinho quando `target/classes` muda. Não derrube o processo, só compile:
```bash
cd backend && ./mvnw -q compile
```
- A compilação também copia `src/main/resources`, então uma migration nova é aplicada pelo Flyway no restart.
- Confirme o restart no log: deve aparecer uma nova linha `Started FinancasApplication` depois da compilação (`grep -n "Started FinancasApplication" logs/local/backend.log | tail -n 2`) e o `/actuator/health` deve voltar a responder `200`. O log continua no mesmo arquivo, sem ser sobrescrito.
- Se a IDE do usuário estiver aberta com build automático, ela pode compilar antes de você. Tudo bem, o efeito é o mesmo.
- Reinicie o processo inteiro (encerre e repita a etapa 3) só nestes casos:
  - mudança no `pom.xml` ou nas dependências;
  - mudança em `application*.properties` que o devtools não aplicou;
  - o restart falhou e o contexto ficou parado.
- **Nunca rode `./mvnw clean` com o backend no ar**: ele apaga o `target/` debaixo do processo. Para limpar, pare o backend antes.
- `./mvnw verify` com o backend no ar também recompila e dispara um restart. Os testes usam o `financas_test`, então não há conflito de banco.

**Frontend:** o `ng serve` recarrega sozinho.

**Ao subir de novo do zero (etapa 3),** o log é sobrescrito. Se o anterior ainda for útil, copie-o antes (`cp logs/local/backend.log logs/local/backend-anterior.log`).

## 6. Encerrar

Quando o usuário disser que terminou ("pode parar", "encerra o ambiente"), encerre os dois processos que você iniciou. O container do banco fica rodando.
