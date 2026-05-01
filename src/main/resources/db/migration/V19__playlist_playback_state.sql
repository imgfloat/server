ALTER TABLE channels ADD COLUMN playlist_current_track_id TEXT;
ALTER TABLE channels ADD COLUMN playlist_is_playing     INTEGER NOT NULL DEFAULT 0;
ALTER TABLE channels ADD COLUMN playlist_is_paused      INTEGER NOT NULL DEFAULT 0;
ALTER TABLE channels ADD COLUMN playlist_track_position REAL    NOT NULL DEFAULT 0;
