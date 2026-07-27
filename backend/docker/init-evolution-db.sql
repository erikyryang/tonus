-- A Evolution API guarda o estado dela (instâncias, sessões, mensagens) em
-- Postgres. Banco separado, e não schema dentro de `tonus`: são dados de um
-- produto de terceiro, com migrations próprias que não passam pelo Flyway
-- daqui — misturar tornaria impossível ler `\dt` e saber o que é do estúdio.
--
-- Roda só na primeira criação do volume do Postgres (docker-entrypoint-initdb.d).
-- Em volume que já existe, criar à mão:
--   docker compose exec db createdb -U tonus evolution
CREATE DATABASE evolution;
