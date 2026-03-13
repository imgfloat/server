ALTER TABLE channels ADD COLUMN max_volume_db REAL NOT NULL DEFAULT 0;

UPDATE channels
SET max_volume_db = 0
WHERE max_volume_db IS NULL;
