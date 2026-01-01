package dev.kruhlmann.imgfloat.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

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
        ensureAssetMediaColumns();
        ensureAuthorizedClientTable();
        normalizeAuthorizedClientTimestamps();
    }

    private void ensureSessionAttributeUpsertTrigger() {
        try {
            jdbcTemplate.execute("""
                CREATE TRIGGER IF NOT EXISTS SPRING_SESSION_ATTRIBUTES_UPSERT
                BEFORE INSERT ON SPRING_SESSION_ATTRIBUTES
                FOR EACH ROW
                BEGIN
                    DELETE FROM SPRING_SESSION_ATTRIBUTES
                    WHERE SESSION_PRIMARY_ID = NEW.SESSION_PRIMARY_ID
                      AND ATTRIBUTE_NAME = NEW.ATTRIBUTE_NAME;
                END;
                """);
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

    private void ensureAssetMediaColumns() {
        List<String> columns;
        try {
            columns = jdbcTemplate.query("PRAGMA table_info(assets)", (rs, rowNum) -> rs.getString("name"));
        } catch (DataAccessException ex) {
            logger.warn("Unable to inspect assets table for media columns", ex);
            return;
        }

        if (columns.isEmpty()) {
            return;
        }

        addColumnIfMissing("assets", columns, "speed", "REAL", "1.0");
        addColumnIfMissing("assets", columns, "muted", "BOOLEAN", "0");
        addColumnIfMissing("assets", columns, "media_type", "TEXT", "'application/octet-stream'");
        addColumnIfMissing("assets", columns, "audio_loop", "BOOLEAN", "0");
        addColumnIfMissing("assets", columns, "audio_delay_millis", "INTEGER", "0");
        addColumnIfMissing("assets", columns, "audio_speed", "REAL", "1.0");
        addColumnIfMissing("assets", columns, "audio_pitch", "REAL", "1.0");
        addColumnIfMissing("assets", columns, "audio_volume", "REAL", "1.0");
        addColumnIfMissing("assets", columns, "preview", "TEXT", "NULL");
    }

    private void addColumnIfMissing(String tableName, List<String> existingColumns, String columnName, String dataType, String defaultValue) {
        if (existingColumns.contains(columnName)) {
            return;
        }

        try {
            jdbcTemplate.execute("ALTER TABLE " + tableName + " ADD COLUMN " + columnName + " " + dataType + " DEFAULT " + defaultValue);
            jdbcTemplate.execute("UPDATE " + tableName + " SET " + columnName + " = " + defaultValue + " WHERE " + columnName + " IS NULL");
            logger.info("Added missing column '{}' to {} table", columnName, tableName);
        } catch (DataAccessException ex) {
            logger.warn("Failed to add column '{}' to {} table", columnName, tableName, ex);
        }
    }

    private void ensureAuthorizedClientTable() {
        try {
            jdbcTemplate.execute("""
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
                """);
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
                            "SET " + column + " = CASE " +
                            "WHEN " + column + " LIKE '%-%' THEN CAST(strftime('%s', " + column + ") AS INTEGER) * 1000 " +
                            "WHEN typeof(" + column + ") = 'text' AND " + column + " GLOB '[0-9]*' THEN CAST(" + column + " AS INTEGER) " +
                            "WHEN typeof(" + column + ") = 'integer' THEN " + column + " " +
                            "ELSE " + column + " END " +
                            "WHERE " + column + " IS NOT NULL");
            if (updated > 0) {
                logger.info("Normalized {} rows in oauth2_authorized_client.{}", updated, column);
            }
        } catch (DataAccessException ex) {
            logger.warn("Unable to normalize oauth2_authorized_client.{} timestamps", column, ex);
        }
    }
}
