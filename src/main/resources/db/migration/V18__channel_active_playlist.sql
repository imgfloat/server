ALTER TABLE channels ADD COLUMN active_playlist_id TEXT REFERENCES playlists(id) ON DELETE SET NULL;
