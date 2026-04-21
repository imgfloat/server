-- OAuth2 authorized client table for storing Twitch OAuth tokens at rest (AES-256-GCM encrypted).
-- This table is managed by SQLiteOAuth2AuthorizedClientService; Spring Session's JDBC
-- auto-initialization is disabled (spring.session.jdbc.initialize-schema=never) so the
-- table must be created here.
CREATE TABLE IF NOT EXISTS oauth2_authorized_client (
    client_registration_id VARCHAR(100) NOT NULL,
    principal_name         VARCHAR(200) NOT NULL,
    access_token_type      VARCHAR(100),
    access_token_value     TEXT,
    access_token_issued_at INTEGER,
    access_token_expires_at INTEGER,
    access_token_scopes    VARCHAR(1000),
    refresh_token_value    TEXT,
    refresh_token_issued_at INTEGER,
    PRIMARY KEY (client_registration_id, principal_name)
);

-- SQLite does not support INSERT OR REPLACE semantics for Spring Session's attribute writes.
-- This trigger deletes any existing row for the same (session, attribute) pair before each
-- INSERT, effectively converting INSERT to an UPSERT.
CREATE TRIGGER IF NOT EXISTS SPRING_SESSION_ATTRIBUTES_UPSERT
BEFORE INSERT ON spring_session_attributes
FOR EACH ROW
BEGIN
    DELETE FROM spring_session_attributes
    WHERE session_primary_id = NEW.session_primary_id
      AND attribute_name = NEW.attribute_name;
END;
