-- Invitation et activation securisee des comptes commercants.

CREATE TABLE IF NOT EXISTS user_invitation (
    id UUID PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    token_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    invited_by VARCHAR(96) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ,
    CONSTRAINT uk_user_invitation_token UNIQUE(token_hash)
);

CREATE INDEX IF NOT EXISTS idx_user_invitation_user
    ON user_invitation(user_id, created_at DESC);
