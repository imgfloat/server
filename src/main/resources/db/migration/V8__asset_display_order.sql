ALTER TABLE assets ADD COLUMN display_order INTEGER;

UPDATE assets
SET display_order = visual_assets.z_index
FROM visual_assets
WHERE assets.id = visual_assets.id
  AND assets.asset_type IN ('IMAGE', 'VIDEO', 'MODEL', 'OTHER');

UPDATE assets
SET display_order = script_assets.z_index
FROM script_assets
WHERE assets.id = script_assets.id
  AND assets.asset_type = 'SCRIPT';

UPDATE assets
SET display_order = 1
WHERE display_order IS NULL;

ALTER TABLE visual_assets DROP COLUMN z_index;
ALTER TABLE script_assets DROP COLUMN z_index;
