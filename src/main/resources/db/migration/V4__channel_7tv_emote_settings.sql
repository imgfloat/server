ALTER TABLE channels ADD COLUMN allow_7tv_emotes_for_assets BOOLEAN NOT NULL DEFAULT TRUE;

UPDATE channels
SET allow_7tv_emotes_for_assets = TRUE
WHERE allow_7tv_emotes_for_assets IS NULL;
