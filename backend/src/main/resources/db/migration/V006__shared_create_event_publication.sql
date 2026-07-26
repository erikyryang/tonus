-- Registry de publicação de eventos do Spring Modulith. Cada evento entregue a
-- um listener vira uma linha aqui, marcada como completa só quando o listener
-- termina; se ele falhar ou o app cair no meio, a linha fica incompleta e o
-- evento pode ser reprocessado. É isso que sustenta a entrega at-least-once.
--
-- O schema espelha o mapeamento de JpaEventPublication (Modulith 1.4.x) porque
-- ddl-auto=validate faz o Hibernate conferir esta tabela como qualquer outra.
CREATE TABLE event_publication (
    id UUID NOT NULL,
    listener_id TEXT NOT NULL,
    event_type TEXT NOT NULL,
    serialized_event TEXT NOT NULL,
    publication_date TIMESTAMP WITH TIME ZONE NOT NULL,
    completion_date TIMESTAMP WITH TIME ZONE,
    PRIMARY KEY (id)
);

-- Busca de publicações pendentes (completion_date IS NULL) na retomada.
CREATE INDEX idx_event_publication_completion_date ON event_publication (completion_date);
CREATE INDEX idx_event_publication_listener_id ON event_publication (listener_id);
