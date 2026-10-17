# Scripts anteriores ao Flyway

Histórico de referência. **Não aplicar** em bancos novos: o schema agora vem de
`backend/src/main/resources/db/migration` (V1 = baseline do banco em 2026-10-17, V2 = regras
destes scripts que não estavam no banco).

Exceção: `seed-categorias.sql` popula categorias e subcategorias padrão para os usuários já
existentes. Usuários novos recebem as mesmas categorias no cadastro (`CategoriasPadraoService`);
o script só serve para backfill e pode ser rodado manualmente com `psql`.
