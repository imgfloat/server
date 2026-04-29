CREATE TABLE playlists (
    id          TEXT    NOT NULL PRIMARY KEY,
    broadcaster TEXT    NOT NULL,
    name        TEXT    NOT NULL,
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE playlist_tracks (
    id             TEXT    NOT NULL PRIMARY KEY,
    playlist_id    TEXT    NOT NULL REFERENCES playlists(id) ON DELETE CASCADE,
    audio_asset_id TEXT    NOT NULL REFERENCES audio_assets(id) ON DELETE CASCADE,
    track_order    INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX idx_playlists_broadcaster ON playlists(broadcaster);
CREATE INDEX idx_playlist_tracks_playlist ON playlist_tracks(playlist_id);
CREATE INDEX idx_playlist_tracks_order    ON playlist_tracks(playlist_id, track_order);
