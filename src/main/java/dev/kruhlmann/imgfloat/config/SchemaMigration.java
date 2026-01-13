package dev.kruhlmann.imgfloat.config;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class SchemaMigration implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(SchemaMigration.class);

    private final JdbcTemplate jdbcTemplate;

    public SchemaMigration(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        ensureSessionAttributeUpsertTrigger();
        ensureChannelCanvasColumns();
        ensureAssetTables();
        ensureAuthorizedClientTable();
        normalizeAuthorizedClientTimestamps();
    }

    private void ensureSessionAttributeUpsertTrigger() {
        try {
            jdbcTemplate.execute(
                """
                CREATE TRIGGER IF NOT EXISTS SPRING_SESSION_ATTRIBUTES_UPSERT
                BEFORE INSERT ON SPRING_SESSION_ATTRIBUTES
                FOR EACH ROW
                BEGIN
                    DELETE FROM SPRING_SESSION_ATTRIBUTES
                    WHERE SESSION_PRIMARY_ID = NEW.SESSION_PRIMARY_ID
                      AND ATTRIBUTE_NAME = NEW.ATTRIBUTE_NAME;
                END;
                """
            );
            logger.info("Ensured SPRING_SESSION_ATTRIBUTES upsert trigger exists");
        } catch (DataAccessException ex) {
            logger.warn("Unable to ensure SPRING_SESSION_ATTRIBUTES upsert trigger", ex);
        }
    }

    private void ensureChannelCanvasColumns() {
        List<String> columns;
        try {
            columns = jdbcTemplate.query("PRAGMA table_info(channels)", (rs, rowNum) -> rs.getString("name"));
        } catch (DataAccessException ex) {
            logger.warn("Unable to inspect channels table for canvas columns", ex);
            return;
        }

        if (columns.isEmpty()) {
            // Table does not exist yet; Hibernate will create it with the correct columns.
            return;
        }

        addColumnIfMissing("channels", columns, "canvas_width", "REAL", "1920");
        addColumnIfMissing("channels", columns, "canvas_height", "REAL", "1080");
    }

    private void ensureAssetTables() {
        List<String> columns;
        try {
            columns = jdbcTemplate.query("PRAGMA table_info(assets)", (rs, rowNum) -> rs.getString("name"));
        } catch (DataAccessException ex) {
            logger.warn("Unable to inspect assets table for asset columns", ex);
            return;
        }

        if (columns.isEmpty()) {
            return;
        }

        String table = "assets";
        addColumnIfMissing(table, columns, "asset_type", "TEXT", "'OTHER'");
        ensureAssetTypeTables(columns);
    }

    private void ensureAssetTypeTables(List<String> assetColumns) {
        try {
            jdbcTemplate.execute(
                """
                CREATE TABLE IF NOT EXISTS visual_assets (
                    id TEXT PRIMARY KEY,
                    name TEXT NOT NULL,
                    preview TEXT,
                    x REAL NOT NULL DEFAULT 0,
                    y REAL NOT NULL DEFAULT 0,
                    width REAL NOT NULL DEFAULT 0,
                    height REAL NOT NULL DEFAULT 0,
                    rotation REAL NOT NULL DEFAULT 0,
                    speed REAL,
                    muted BOOLEAN,
                    media_type TEXT,
                    original_media_type TEXT,
                    z_index INTEGER,
                    audio_volume REAL,
                    hidden BOOLEAN
                )
                """
            );
            jdbcTemplate.execute(
                """
                CREATE TABLE IF NOT EXISTS audio_assets (
                    id TEXT PRIMARY KEY,
                    name TEXT NOT NULL,
                    media_type TEXT,
                    original_media_type TEXT,
                    audio_loop BOOLEAN,
                    audio_delay_millis INTEGER,
                    audio_speed REAL,
                    audio_pitch REAL,
                    audio_volume REAL,
                    hidden BOOLEAN
                )
                """
            );
            jdbcTemplate.execute(
                """
                CREATE TABLE IF NOT EXISTS script_assets (
                    id TEXT PRIMARY KEY,
                    name TEXT NOT NULL,
                    description TEXT,
                    is_public BOOLEAN,
                    media_type TEXT,
                    original_media_type TEXT,
                    logo_file_id TEXT,
                    source_file_id TEXT
                )
                """
            );
            jdbcTemplate.execute(
                """
                CREATE TABLE IF NOT EXISTS script_asset_attachments (
                    id TEXT PRIMARY KEY,
                    script_asset_id TEXT NOT NULL,
                    file_id TEXT,
                    name TEXT NOT NULL,
                    media_type TEXT,
                    original_media_type TEXT,
                    asset_type TEXT
                )
                """
            );
            jdbcTemplate.execute(
                """
                CREATE TABLE IF NOT EXISTS script_asset_files (
                    id TEXT PRIMARY KEY,
                    broadcaster TEXT NOT NULL,
                    media_type TEXT,
                    original_media_type TEXT,
                    asset_type TEXT NOT NULL
                )
                """
            );
            ensureScriptAssetColumns();
            backfillAssetTypes(assetColumns);
        } catch (DataAccessException ex) {
            logger.warn("Unable to ensure asset type tables", ex);
        }
    }

    private void ensureScriptAssetColumns() {
        List<String> scriptColumns;
        List<String> attachmentColumns;
        try {
            scriptColumns = jdbcTemplate.query("PRAGMA table_info(script_assets)", (rs, rowNum) -> rs.getString("name"));
            attachmentColumns =
                jdbcTemplate.query("PRAGMA table_info(script_asset_attachments)", (rs, rowNum) -> rs.getString("name"));
        } catch (DataAccessException ex) {
            logger.warn("Unable to inspect script asset tables", ex);
            return;
        }

        if (!scriptColumns.isEmpty()) {
            addColumnIfMissing("script_assets", scriptColumns, "description", "TEXT", "NULL");
            addColumnIfMissing("script_assets", scriptColumns, "is_public", "BOOLEAN", "0");
            addColumnIfMissing("script_assets", scriptColumns, "logo_file_id", "TEXT", "NULL");
            addColumnIfMissing("script_assets", scriptColumns, "source_file_id", "TEXT", "NULL");
        }

        if (!attachmentColumns.isEmpty()) {
            addColumnIfMissing("script_asset_attachments", attachmentColumns, "file_id", "TEXT", "NULL");
        }

        try {
            jdbcTemplate.execute("UPDATE script_assets SET source_file_id = id WHERE source_file_id IS NULL");
            jdbcTemplate.execute(
                """
                INSERT OR IGNORE INTO script_asset_files (
                    id, broadcaster, media_type, original_media_type, asset_type
                )
                SELECT s.id, a.broadcaster, s.media_type, s.original_media_type, 'SCRIPT'
                FROM script_assets s
                JOIN assets a ON a.id = s.id
                """
            );
            jdbcTemplate.execute(
                """
                INSERT OR IGNORE INTO script_asset_files (
                    id, broadcaster, media_type, original_media_type, asset_type
                )
                SELECT sa.id, a.broadcaster, sa.media_type, sa.original_media_type, sa.asset_type
                FROM script_asset_attachments sa
                JOIN assets a ON a.id = sa.script_asset_id
                """
            );
            jdbcTemplate.execute("UPDATE script_asset_attachments SET file_id = id WHERE file_id IS NULL");
        } catch (DataAccessException ex) {
            logger.warn("Unable to backfill script asset files", ex);
        }
    }

    private void backfillAssetTypes(List<String> assetColumns) {
        if (!assetColumns.contains("media_type")) {
            return;
        }
        try {
            jdbcTemplate.execute(
                """
                UPDATE assets
                SET asset_type = CASE
                    WHEN media_type LIKE 'audio/%' THEN 'AUDIO'
                    WHEN media_type LIKE 'video/%' THEN 'VIDEO'
                    WHEN media_type LIKE 'image/%' THEN 'IMAGE'
                    WHEN media_type LIKE 'model/%' THEN 'MODEL'
                    WHEN media_type LIKE 'application/javascript%' THEN 'SCRIPT'
                    WHEN media_type LIKE 'text/javascript%' THEN 'SCRIPT'
                    ELSE COALESCE(asset_type, 'OTHER')
                END
                WHERE asset_type IS NULL OR asset_type = '' OR asset_type = 'OTHER'
                """
            );
            jdbcTemplate.execute(
                """
                INSERT OR IGNORE INTO visual_assets (
                    id, name, preview, x, y, width, height, rotation, speed, muted, media_type,
                    original_media_type, z_index, audio_volume, hidden
                )
                SELECT id, name, preview, x, y, width, height, rotation, speed, muted, media_type,
                       original_media_type, z_index, audio_volume, hidden
                FROM assets
                WHERE asset_type IN ('IMAGE', 'VIDEO', 'MODEL', 'OTHER')
                """
            );
            jdbcTemplate.execute(
                """
                INSERT OR IGNORE INTO audio_assets (
                    id, name, media_type, original_media_type, audio_loop, audio_delay_millis,
                    audio_speed, audio_pitch, audio_volume, hidden
                )
                SELECT id, name, media_type, original_media_type, audio_loop, audio_delay_millis,
                       audio_speed, audio_pitch, audio_volume, hidden
                FROM assets
                WHERE asset_type = 'AUDIO'
                """
            );
            jdbcTemplate.execute(
                """
                INSERT OR IGNORE INTO script_assets (
                    id, name, media_type, original_media_type
                )
                SELECT id, name, media_type, original_media_type
                FROM assets
                WHERE asset_type = 'SCRIPT'
                """
            );
        } catch (DataAccessException ex) {
            logger.warn("Unable to backfill asset type tables", ex);
        }
    }

    private void addColumnIfMissing(
        String tableName,
        List<String> existingColumns,
        String columnName,
        String dataType,
        String defaultValue
    ) {
        if (existingColumns.contains(columnName)) {
            return;
        }

        // SECURITY: This is ok, because tableName and columnName are controlled internally and not from user input.
        try {
            jdbcTemplate.execute(
                "ALTER TABLE " + tableName + " ADD COLUMN " + columnName + " " + dataType + " DEFAULT " + defaultValue
            );
            jdbcTemplate.execute(
                "UPDATE " +
                    tableName +
                    " SET " +
                    columnName +
                    " = " +
                    defaultValue +
                    " WHERE " +
                    columnName +
                    " IS NULL"
            );
            logger.info("Added missing column '{}' to {} table", columnName, tableName);
        } catch (DataAccessException ex) {
            logger.warn("Failed to add column '{}' to {} table", columnName, tableName, ex);
        }
    }

    private void ensureAuthorizedClientTable() {
        try {
            jdbcTemplate.execute(
                """
                CREATE TABLE IF NOT EXISTS oauth2_authorized_client (
                    client_registration_id VARCHAR(100) NOT NULL,
                    principal_name VARCHAR(200) NOT NULL,
                    access_token_type VARCHAR(100),
                    access_token_value TEXT,
                    access_token_issued_at INTEGER,
                    access_token_expires_at INTEGER,
                    access_token_scopes VARCHAR(1000),
                    refresh_token_value TEXT,
                    refresh_token_issued_at INTEGER,
                    PRIMARY KEY (client_registration_id, principal_name)
                )
                """
            );
            logger.info("Ensured oauth2_authorized_client table exists");
        } catch (DataAccessException ex) {
            logger.warn("Unable to ensure oauth2_authorized_client table", ex);
        }
    }

    private void normalizeAuthorizedClientTimestamps() {
        normalizeTimestampColumn("access_token_issued_at");
        normalizeTimestampColumn("access_token_expires_at");
        normalizeTimestampColumn("refresh_token_issued_at");
    }

    private void normalizeTimestampColumn(String column) {
        try {
            int updated = jdbcTemplate.update(
                "UPDATE oauth2_authorized_client " +
                    "SET " +
                    column +
                    " = CASE " +
                    "WHEN " +
                    column +
                    " LIKE '%-%' THEN CAST(strftime('%s', " +
                    column +
                    ") AS INTEGER) * 1000 " +
                    "WHEN typeof(" +
                    column +
                    ") = 'text' AND " +
                    column +
                    " GLOB '[0-9]*' THEN CAST(" +
                    column +
                    " AS INTEGER) " +
                    "WHEN typeof(" +
                    column +
                    ") = 'integer' THEN " +
                    column +
                    " " +
                    "ELSE " +
                    column +
                    " END " +
                    "WHERE " +
                    column +
                    " IS NOT NULL"
            );
            if (updated > 0) {
                logger.info("Normalized {} rows in oauth2_authorized_client.{}", updated, column);
            }
        } catch (DataAccessException ex) {
            logger.warn("Unable to normalize oauth2_authorized_client.{} timestamps", column, ex);
        }
    }
}
