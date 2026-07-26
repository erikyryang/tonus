-- Acesso revogado quando a pessoa sai do estúdio. Sem esta coluna não havia
-- como negar login a quem foi desativado no cadastro — a única saída era
-- apagar a linha, o que levaria junto o histórico de quem fez o quê.
ALTER TABLE users ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE;

-- O listener de revogação busca o usuário pelo vínculo, não pelo login.
CREATE INDEX idx_users_student_id ON users (student_id);
CREATE INDEX idx_users_teacher_id ON users (teacher_id);
