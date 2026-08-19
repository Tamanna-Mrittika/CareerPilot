-- identity-service owns the 'identity' schema exclusively. No other service holds grants
-- on it, so cross-service reads are impossible by construction rather than by convention.

CREATE TABLE user_account (
    id            UUID PRIMARY KEY,
    email         VARCHAR(254) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    full_name     VARCHAR(120) NOT NULL,
    enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- Functional unique index rather than a plain UNIQUE constraint: the application
-- lower-cases on write, and this guarantees it even if some future code path forgets.
CREATE UNIQUE INDEX ux_user_account_email ON user_account (LOWER(email));

CREATE TABLE user_role (
    user_id UUID        NOT NULL REFERENCES user_account (id) ON DELETE CASCADE,
    role    VARCHAR(32) NOT NULL,
    PRIMARY KEY (user_id, role)
);

CREATE TABLE refresh_token (
    id         UUID PRIMARY KEY,
    user_id    UUID         NOT NULL REFERENCES user_account (id) ON DELETE CASCADE,
    -- SHA-256, base64: the raw token is never persisted anywhere.
    token_hash VARCHAR(64)  NOT NULL,
    family_id  UUID         NOT NULL,
    expires_at TIMESTAMPTZ  NOT NULL,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX ux_refresh_token_hash ON refresh_token (token_hash);
-- Reuse detection revokes by family, so that column is the hot lookup path.
CREATE INDEX ix_refresh_token_family ON refresh_token (family_id);
CREATE INDEX ix_refresh_token_user ON refresh_token (user_id);
CREATE INDEX ix_refresh_token_expires ON refresh_token (expires_at);
