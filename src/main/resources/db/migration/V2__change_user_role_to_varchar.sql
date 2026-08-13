-- Postgres will not implicitly cast varchar to a named enum, so Hibernate's
-- @Enumerated(EnumType.STRING) binding was rejected on insert.
-- USING role::text preserves the values of any existing rows.
ALTER TABLE users
    ALTER COLUMN role TYPE VARCHAR(20) USING role::text;

ALTER TABLE users
    ALTER COLUMN role SET DEFAULT 'MEMBER';

ALTER TABLE users
    ADD CONSTRAINT users_role_check CHECK (role IN ('ADMIN', 'MEMBER'));

-- Only droppable once no column references it
DROP TYPE user_role;

-- The entity declares enabled as nullable = false; V1 only gave it a DEFAULT
UPDATE users SET enabled = TRUE WHERE enabled IS NULL;

ALTER TABLE users
    ALTER COLUMN enabled SET NOT NULL;
