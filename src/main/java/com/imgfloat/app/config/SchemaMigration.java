package com.imgfloat.app.config;

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
        ensureChannelCanvasColumns();
        ensureAssetMediaColumns();
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
}

