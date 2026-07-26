-- teacher_id é NOT NULL de propósito: o vínculo aluno->professor é regra de
-- negócio da SPEC-002 e precisa valer mesmo para escrita que não passe pela API.
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
