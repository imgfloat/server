CREATE TABLE IF NOT EXISTS script_asset_allowed_domains (
    script_asset_id TEXT NOT NULL,
    allowed_domain TEXT NOT NULL,
    PRIMARY KEY (script_asset_id, allowed_domain),
    FOREIGN KEY (script_asset_id) REFERENCES script_assets(id) ON DELETE CASCADE
);
