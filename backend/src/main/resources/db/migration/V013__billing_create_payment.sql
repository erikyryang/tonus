-- Write-off of an invoice: when it was paid and who registered it.
--
-- registered_by stores the id of the ADMIN user who gave the write-off,
-- without FK: it is a loose reference, same pattern as student_id/teacher_id
-- in other tables — billing cannot depend on auth (would close a cycle with
-- auth->registration).
CREATE TABLE payment (
    id BIGSERIAL PRIMARY KEY,
    invoice_id BIGINT NOT NULL REFERENCES invoice (id),
    payment_date DATE NOT NULL,
    registered_by BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    -- An invoice can only be paid once: it is the acceptance criterion
    -- "write-off on already paid invoice -> 409" expressed in the database.
    CONSTRAINT uk_payment_invoice UNIQUE (invoice_id)
);
