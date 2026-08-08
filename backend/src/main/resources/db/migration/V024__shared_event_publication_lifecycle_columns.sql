-- Spring Modulith 2.x adds a lifecycle to the event registry: each row now
-- carries status, a redelivery attempt counter and the last redelivery date.
-- The schema mirrors JpaEventPublication (Modulith 2.1.x), the same way V006
-- mirrored version 1.4.x — ddl-auto=validate checks this table like any
-- other.
--
-- EventRepublicationJob and EventPublicationCleanupJob keep working without
-- change: Modulith's internal queries for "incomplete" and "delete completed"
-- still use completion_date, not status. The backfill below exists because a
-- null status would make the internal redelivery UPDATE (which compares
-- "status != RESUBMITTED") not find the row — NULL doesn't match "!=" in SQL.
ALTER TABLE event_publication
    ADD COLUMN status VARCHAR(255),
    ADD COLUMN last_resubmission_date TIMESTAMP WITH TIME ZONE,
    ADD COLUMN completion_attempts INTEGER NOT NULL DEFAULT 0;

UPDATE event_publication
   SET status = CASE WHEN completion_date IS NOT NULL THEN 'COMPLETED' ELSE 'PUBLISHED' END;
