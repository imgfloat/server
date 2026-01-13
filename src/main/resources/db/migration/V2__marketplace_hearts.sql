CREATE TABLE IF NOT EXISTS marketplace_script_hearts (
    script_id TEXT NOT NULL,
    username TEXT NOT NULL,
    PRIMARY KEY (script_id, username)
);
