CREATE TABLE copyright_reports (
    id TEXT PRIMARY KEY,
    asset_id TEXT NOT NULL REFERENCES assets(id) ON DELETE CASCADE,
    broadcaster TEXT NOT NULL,
    claimant_name TEXT NOT NULL,
    claimant_email TEXT NOT NULL,
    original_work_description TEXT NOT NULL,
    infringing_description TEXT NOT NULL,
    good_faith_declaration BOOLEAN NOT NULL DEFAULT FALSE,
    status TEXT NOT NULL DEFAULT 'PENDING',
    resolution_notes TEXT,
    resolved_by TEXT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_copyright_reports_asset_id ON copyright_reports(asset_id);
CREATE INDEX idx_copyright_reports_broadcaster ON copyright_reports(broadcaster);
CREATE INDEX idx_copyright_reports_status ON copyright_reports(status);
