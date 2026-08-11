-- WhatsApp gateway settings chosen by the ADMIN (SPEC-013).
--
-- Single row guaranteed by constraint, not by convention — same reasoning as
-- billing_settings: with two rows, "which instance counts?" would have
-- "whichever LIMIT 1 picked" as the answer, and the answer decides where every
-- message to every student goes.
CREATE TABLE whatsapp_settings (
    id BIGINT PRIMARY KEY,
    instance_name VARCHAR(100) NOT NULL,
    desired_phone_version VARCHAR(40) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT ck_whatsapp_settings_singleton CHECK (id = 1),
    CONSTRAINT ck_whatsapp_settings_instance CHECK (length(trim(instance_name)) > 0)
);

-- Seeded with the values that used to come from configuration, so there is no
-- state without a row. From here on the table is the authority and
-- EVOLUTION_INSTANCE is only the initial value — otherwise the screen would
-- offer a choice that sending ignores.
INSERT INTO whatsapp_settings (id, instance_name, desired_phone_version, created_at, updated_at)
VALUES (1, 'tonus', '2.3000.1043857760', now(), now());
