-- Spring Modulith event publication registry. Each event delivered to a
-- listener becomes a row here, marked as completed only when the listener
-- finishes; if it fails or the app crashes midway, the row stays incomplete
-- and the event can be reprocessed. That is what sustains at-least-once
-- delivery.
--
-- The schema mirrors the JpaEventPublication mapping (Modulith 1.4.x) because
-- ddl-auto=validate makes Hibernate check this table like any other.
CREATE TABLE event_publication (
    id UUID NOT NULL,
    listener_id TEXT NOT NULL,
    event_type TEXT NOT NULL,
    serialized_event TEXT NOT NULL,
    publication_date TIMESTAMP WITH TIME ZONE NOT NULL,
    completion_date TIMESTAMP WITH TIME ZONE,
    PRIMARY KEY (id)
);

-- Lookup of pending publications (completion_date IS NULL) on resume.
CREATE INDEX idx_event_publication_completion_date ON event_publication (completion_date);
CREATE INDEX idx_event_publication_listener_id ON event_publication (listener_id);
