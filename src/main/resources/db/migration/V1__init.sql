CREATE TABLE IF NOT EXISTS assets (
    id TEXT PRIMARY KEY,
    broadcaster TEXT NOT NULL,
    asset_type TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS visual_assets (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    preview TEXT,
    x REAL NOT NULL,
    y REAL NOT NULL,
    width REAL NOT NULL,
    height REAL NOT NULL,
    rotation REAL NOT NULL,
    speed REAL,
    muted BOOLEAN,
    media_type TEXT,
    original_media_type TEXT,
    z_index INTEGER,
    audio_volume REAL,
    hidden BOOLEAN NOT NULL
);

CREATE TABLE IF NOT EXISTS audio_assets (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    media_type TEXT,
    original_media_type TEXT,
    audio_loop BOOLEAN,
    audio_delay_millis INTEGER,
    audio_speed REAL,
    audio_pitch REAL,
    audio_volume REAL,
    hidden BOOLEAN NOT NULL
);

CREATE TABLE IF NOT EXISTS script_assets (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    description TEXT,
    is_public BOOLEAN,
    media_type TEXT,
    original_media_type TEXT,
    logo_file_id TEXT,
    source_file_id TEXT
);

CREATE TABLE IF NOT EXISTS script_asset_files (
    id TEXT PRIMARY KEY,
    broadcaster TEXT NOT NULL,
    media_type TEXT,
    original_media_type TEXT,
    asset_type TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS script_asset_attachments (
    id TEXT PRIMARY KEY,
    script_asset_id TEXT NOT NULL,
    file_id TEXT,
    name TEXT NOT NULL,
    media_type TEXT,
    original_media_type TEXT,
    asset_type TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS channels (
    broadcaster TEXT PRIMARY KEY,
    canvas_width REAL NOT NULL,
    canvas_height REAL NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS channel_admins (
    channel_id TEXT NOT NULL,
    admin_username TEXT NOT NULL,
    PRIMARY KEY (channel_id, admin_username),
    FOREIGN KEY (channel_id) REFERENCES channels(broadcaster) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS settings (
    id INTEGER PRIMARY KEY,
    min_asset_playback_speed_fraction REAL NOT NULL,
    max_asset_playback_speed_fraction REAL NOT NULL,
    min_asset_audio_pitch_fraction REAL NOT NULL,
    max_asset_audio_pitch_fraction REAL NOT NULL,
    min_asset_volume_fraction REAL NOT NULL,
    max_asset_volume_fraction REAL NOT NULL,
    max_canvas_side_length_pixels INTEGER NOT NULL,
    canvas_frames_per_second INTEGER NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS system_administrators (
    id TEXT PRIMARY KEY,
    twitch_username TEXT NOT NULL UNIQUE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS spring_session (
    primary_id TEXT NOT NULL,
    session_id TEXT NOT NULL,
    creation_time INTEGER NOT NULL,
    last_access_time INTEGER NOT NULL,
    max_inactive_interval INTEGER NOT NULL,
    expiry_time INTEGER NOT NULL,
    principal_name TEXT,
    PRIMARY KEY (primary_id)
);

CREATE UNIQUE INDEX IF NOT EXISTS spring_session_ix1 ON spring_session (session_id);
CREATE INDEX IF NOT EXISTS spring_session_ix2 ON spring_session (expiry_time);
CREATE INDEX IF NOT EXISTS spring_session_ix3 ON spring_session (principal_name);

CREATE TABLE IF NOT EXISTS spring_session_attributes (
    session_primary_id TEXT NOT NULL,
    attribute_name TEXT NOT NULL,
    attribute_bytes BLOB NOT NULL,
    PRIMARY KEY (session_primary_id, attribute_name),
    FOREIGN KEY (session_primary_id) REFERENCES spring_session(primary_id) ON DELETE CASCADE
);
