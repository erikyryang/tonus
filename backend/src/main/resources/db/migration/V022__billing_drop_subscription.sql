-- The subscription dies last (SPEC-012).
--
-- Only here, after plans exist (V019), the data migrated (V020) and the
-- invoice already points to the student (V021). Dropping it earlier would
-- leave the two previous migrations without the source they read from.
DROP TABLE subscription;
