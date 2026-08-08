-- Access revoked when the person leaves the studio. Without this column there
-- was no way to deny login to someone deactivated at registration — the only
-- way out was to delete the row, which would take the history of who did what
-- along with it.
ALTER TABLE users ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE;

-- The revocation listener looks up the user by the link, not by the login.
CREATE INDEX idx_users_student_id ON users (student_id);
CREATE INDEX idx_users_teacher_id ON users (teacher_id);
