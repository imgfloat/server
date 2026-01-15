ALTER TABLE script_assets ADD COLUMN IF NOT EXISTS z_index INTEGER NOT NULL DEFAULT 1;

UPDATE script_assets
SET z_index = 1
WHERE z_index IS NULL;
