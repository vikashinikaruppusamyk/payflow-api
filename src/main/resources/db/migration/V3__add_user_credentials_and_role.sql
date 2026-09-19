-- Credentials and roles for JWT authentication.
-- password_hash holds a BCrypt hash, never the password itself. It is nullable only because accounts
-- registered before authentication existed have no password; such accounts cannot log in.
ALTER TABLE app_user ADD COLUMN password_hash VARCHAR(100);
ALTER TABLE app_user ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT 'USER';
ALTER TABLE app_user ADD CONSTRAINT ck_app_user_role CHECK (role IN ('USER', 'ADMIN'));
