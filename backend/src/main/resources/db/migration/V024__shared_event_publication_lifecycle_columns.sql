-- Spring Modulith 2.x acrescenta ciclo de vida ao registry de eventos: cada
-- linha passa a carregar status, contador de tentativas de reentrega e data
-- da última reentrega. O schema espelha JpaEventPublication (Modulith 2.1.x),
-- do mesmo jeito que V006 espelhava a versão 1.4.x — ddl-auto=validate confere
-- esta tabela como qualquer outra.
--
-- EventRepublicationJob e EventPublicationCleanupJob continuam funcionando
-- sem mudança: as consultas internas do Modulith para "incompleto" e "apagar
-- concluído" ainda usam completion_date, não status. O backfill abaixo existe
-- porque status nulo faria o UPDATE interno de reentrega (que compara
-- "status != RESUBMITTED") não achar a linha — NULL não bate com "!=" em SQL.
ALTER TABLE event_publication
    ADD COLUMN status VARCHAR(255),
    ADD COLUMN last_resubmission_date TIMESTAMP WITH TIME ZONE,
    ADD COLUMN completion_attempts INTEGER NOT NULL DEFAULT 0;

UPDATE event_publication
   SET status = CASE WHEN completion_date IS NOT NULL THEN 'COMPLETED' ELSE 'PUBLISHED' END;
