CREATE TABLE IF NOT EXISTS channel_audit_log (
    id TEXT PRIMARY KEY,
    broadcaster TEXT NOT NULL,
    actor TEXT,
    action TEXT NOT NULL,
    details TEXT,
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS channel_audit_log_broadcaster_idx ON channel_audit_log (broadcaster);
CREATE INDEX IF NOT EXISTS channel_audit_log_created_at_idx ON channel_audit_log (created_at);
