-- One-time data migration: normalize oauth2_authorized_client timestamp columns from
-- ISO-8601 strings or text epoch values to integer milliseconds-since-epoch.
-- This corrects records written by earlier versions of the application that stored
-- Instant values as strings rather than longs.
UPDATE oauth2_authorized_client
SET access_token_issued_at = CASE
    WHEN access_token_issued_at LIKE '%-%' THEN CAST(strftime('%s', access_token_issued_at) AS INTEGER) * 1000
    WHEN typeof(access_token_issued_at) = 'text' AND access_token_issued_at GLOB '[0-9]*' THEN CAST(access_token_issued_at AS INTEGER)
    ELSE access_token_issued_at
END
WHERE access_token_issued_at IS NOT NULL;

UPDATE oauth2_authorized_client
SET access_token_expires_at = CASE
    WHEN access_token_expires_at LIKE '%-%' THEN CAST(strftime('%s', access_token_expires_at) AS INTEGER) * 1000
    WHEN typeof(access_token_expires_at) = 'text' AND access_token_expires_at GLOB '[0-9]*' THEN CAST(access_token_expires_at AS INTEGER)
    ELSE access_token_expires_at
END
WHERE access_token_expires_at IS NOT NULL;

UPDATE oauth2_authorized_client
SET refresh_token_issued_at = CASE
    WHEN refresh_token_issued_at LIKE '%-%' THEN CAST(strftime('%s', refresh_token_issued_at) AS INTEGER) * 1000
    WHEN typeof(refresh_token_issued_at) = 'text' AND refresh_token_issued_at GLOB '[0-9]*' THEN CAST(refresh_token_issued_at AS INTEGER)
    ELSE refresh_token_issued_at
END
WHERE refresh_token_issued_at IS NOT NULL;
