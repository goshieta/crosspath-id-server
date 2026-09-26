CREATE TABLE id_allocator (
    singleton SMALLINT PRIMARY KEY CHECK (singleton = 1),
    next_id INTEGER NOT NULL CHECK (next_id BETWEEN 1 AND 16777216)
);
INSERT INTO id_allocator(singleton, next_id) VALUES (1, 1);

CREATE TABLE registrations (
    user_id INTEGER PRIMARY KEY CHECK (user_id BETWEEN 1 AND 16777215),
    request_id UUID NOT NULL UNIQUE,
    credential_hash BYTEA NOT NULL UNIQUE
        CHECK (octet_length(credential_hash) = 32),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    state TEXT NOT NULL DEFAULT 'ACTIVE'
        CHECK (state IN ('ACTIVE', 'RETIRED'))
);