-- teacher_id is NOT NULL on purpose: the student->teacher link is a SPEC-002
-- business rule and must hold even for writes that don't go through the API.
CREATE TABLE student (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    phone VARCHAR(20) NOT NULL,
    teacher_id BIGINT NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_student_teacher FOREIGN KEY (teacher_id) REFERENCES teacher (id)
);

CREATE INDEX idx_student_teacher_id ON student (teacher_id);
CREATE INDEX idx_student_active ON student (active);
