-- NightShift Launcher: initial schema.
-- Creates all core tables described in design.md (accounts, sessions,
-- activation keys, login attempts, password reset requests, online
-- heartbeats, telemetry events, crash reports) along with the indexes
-- referenced by the auth/key/online services.

-- gen_random_uuid() lives in the pgcrypto extension on PostgreSQL <13;
-- it is also fine on newer versions. Creating the extension is idempotent.
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- ---------------------------------------------------------------------------
-- accounts
-- ---------------------------------------------------------------------------
CREATE TABLE accounts (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    login         VARCHAR(20)  NOT NULL UNIQUE,
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash TEXT         NOT NULL,
    hwid          VARCHAR(64)  NOT NULL,
    hwid_status   VARCHAR(20)  NOT NULL DEFAULT 'locked',
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_accounts_login ON accounts (login);
CREATE INDEX idx_accounts_email ON accounts (email);

-- ---------------------------------------------------------------------------
-- sessions
-- ---------------------------------------------------------------------------
CREATE TABLE sessions (
    id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id UUID        NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    token      VARCHAR(64) NOT NULL UNIQUE,
    hwid       VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ NOT NULL,
    is_valid   BOOLEAN     NOT NULL DEFAULT true
);

CREATE INDEX idx_sessions_token   ON sessions (token);
CREATE INDEX idx_sessions_account ON sessions (account_id);

-- ---------------------------------------------------------------------------
-- activation_keys
-- ---------------------------------------------------------------------------
CREATE TABLE activation_keys (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    key_value    VARCHAR(32)  NOT NULL UNIQUE,
    key_type     VARCHAR(10)  NOT NULL,
    account_id   UUID         REFERENCES accounts(id) ON DELETE SET NULL,
    hwid         VARCHAR(64),
    activated_at TIMESTAMPTZ,
    expires_at   TIMESTAMPTZ,
    status       VARCHAR(10)  NOT NULL DEFAULT 'unused',
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_activation_keys_value   ON activation_keys (key_value);
CREATE INDEX idx_activation_keys_account ON activation_keys (account_id);

-- ---------------------------------------------------------------------------
-- login_attempts (rate limiting)
-- ---------------------------------------------------------------------------
CREATE TABLE login_attempts (
    id           BIGSERIAL    PRIMARY KEY,
    login        VARCHAR(20)  NOT NULL,
    ip_address   INET         NOT NULL,
    attempted_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    success      BOOLEAN      NOT NULL
);

CREATE INDEX idx_login_attempts_login_time ON login_attempts (login, attempted_at);

-- ---------------------------------------------------------------------------
-- password_reset_requests
-- ---------------------------------------------------------------------------
CREATE TABLE password_reset_requests (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id      UUID        NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    comment         TEXT,
    reset_code      VARCHAR(8),
    code_expires_at TIMESTAMPTZ,
    status          VARCHAR(20) NOT NULL DEFAULT 'pending',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------------------------
-- online_heartbeats
-- ---------------------------------------------------------------------------
CREATE TABLE online_heartbeats (
    session_id UUID        PRIMARY KEY REFERENCES sessions(id) ON DELETE CASCADE,
    last_seen  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------------------------
-- telemetry_events
-- ---------------------------------------------------------------------------
CREATE TABLE telemetry_events (
    id         BIGSERIAL    PRIMARY KEY,
    session_id UUID,
    event_type VARCHAR(50)  NOT NULL,
    payload    JSONB,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_telemetry_events_created ON telemetry_events (created_at);

-- ---------------------------------------------------------------------------
-- crash_reports
-- ---------------------------------------------------------------------------
CREATE TABLE crash_reports (
    id             BIGSERIAL    PRIMARY KEY,
    session_id     UUID,
    stack_trace    TEXT         NOT NULL,
    client_version VARCHAR(20),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
