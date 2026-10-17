# Scripts anteriores ao Flyway

Histórico de referência. **Não aplicar** em bancos novos: o schema agora vem de
`backend/src/main/resources/db/migration` (V1 = baseline do banco em 2026-10-17, V2 = regras
destes scripts que não estavam no banco).

Exceção: `seed-categorias.sql` popula categorias e subcategorias padrão para os usuários já
existentes. Não é migration (os dados dependem de cada usuário) e pode ser rodado manualmente
com `psql` quando necessário.
