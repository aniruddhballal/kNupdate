-- kNupdate v1 schema.
-- Activities intentionally have no updated_at, deleted_at, archive, or history table.

CREATE TABLE users (
    id UUID PRIMARY KEY,
    phone_hash TEXT NOT NULL UNIQUE,
    display_name VARCHAR(40) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE devices (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    fcm_token TEXT NOT NULL,
    platform TEXT NOT NULL CHECK (platform IN ('android')),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE UNIQUE INDEX devices_user_token_idx ON devices(user_id, fcm_token);

CREATE TABLE connections (
    id UUID PRIMARY KEY,
    user_a_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    user_b_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT connections_canonical_pair CHECK (user_a_id < user_b_id),
    CONSTRAINT connections_unique_pair UNIQUE (user_a_id, user_b_id)
);

CREATE TABLE connection_requests (
    id UUID PRIMARY KEY,
    requester_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    target_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    status TEXT NOT NULL CHECK (status IN ('pending', 'accepted', 'declined')),
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT connection_requests_distinct_users CHECK (requester_id <> target_id)
);

CREATE UNIQUE INDEX connection_requests_one_pending_pair_idx
    ON connection_requests (
        LEAST(requester_id, target_id),
        GREATEST(requester_id, target_id)
    )
    WHERE status = 'pending';

CREATE TABLE connection_codes (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    code_hash TEXT NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ
);

CREATE TABLE activities (
    id UUID PRIMARY KEY,
    author_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    activity_type TEXT NOT NULL CHECK (
        activity_type IN (
            'run', 'walk', 'cycle', 'swim', 'strength_training', 'yoga',
            'sport_game', 'study', 'read', 'work', 'cook', 'travel', 'rest', 'other'
        )
    ),
    started_at TIMESTAMPTZ NOT NULL,
    ended_at TIMESTAMPTZ NOT NULL,
    quantity_type TEXT CHECK (quantity_type IN ('distance', 'pages', 'minutes', 'sessions')),
    quantity_value NUMERIC,
    quantity_unit TEXT,
    timezone_at_creation TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT activities_time_order CHECK (ended_at > started_at),
    CONSTRAINT activities_max_duration CHECK (ended_at <= started_at + INTERVAL '24 hours'),
    CONSTRAINT activities_positive_quantity CHECK (quantity_value IS NULL OR quantity_value > 0),
    CONSTRAINT activities_quantity_pair CHECK (
        (quantity_type IS NULL AND quantity_value IS NULL AND quantity_unit IS NULL)
        OR (quantity_type IS NOT NULL AND quantity_value IS NOT NULL AND quantity_unit IS NOT NULL)
    )
);

CREATE INDEX activities_feed_idx ON activities(author_id, created_at DESC, expires_at);
CREATE INDEX activities_expiry_idx ON activities(expires_at);
