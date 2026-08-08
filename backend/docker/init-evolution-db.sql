-- The Evolution API keeps its state (instances, sessions, messages) in
-- Postgres. A separate database, not a schema inside `tonus`: this is data
-- from a third-party product, with its own migrations that don't go through
-- Flyway here — mixing them would make it impossible to read `\dt` and tell
-- what belongs to the studio.
--
-- Runs only on the Postgres volume's first creation (docker-entrypoint-initdb.d).
-- On a volume that already exists, create it by hand:
--   docker compose exec db createdb -U tonus evolution
CREATE DATABASE evolution;
