ALTER TABLE channels ADD COLUMN playlist_current_track_id TEXT;
ALTER TABLE channels ADD COLUMN playlist_is_playing     BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE channels ADD COLUMN playlist_is_paused      BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE channels ADD COLUMN playlist_track_position REAL    NOT NULL DEFAULT 0;
