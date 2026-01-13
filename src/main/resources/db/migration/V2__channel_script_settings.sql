ALTER TABLE channels ADD COLUMN allow_channel_emotes_for_assets BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE channels ADD COLUMN allow_script_chat_access BOOLEAN NOT NULL DEFAULT TRUE;

UPDATE channels
SET allow_channel_emotes_for_assets = TRUE
WHERE allow_channel_emotes_for_assets IS NULL;

UPDATE channels
SET allow_script_chat_access = TRUE
WHERE allow_script_chat_access IS NULL;
