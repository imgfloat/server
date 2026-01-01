package dev.kruhlmann.imgfloat.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class VersionService {
    private static final Logger LOG = LoggerFactory.getLogger(VersionService.class);
    private final String version;
    private final String releaseVersion;

    public VersionService() {
        this.version = resolveVersion();
        this.releaseVersion = normalizeReleaseVersion(this.version);
    }

    public String getVersion() {
        return version;
    }

    public String getReleaseVersion() {
        return releaseVersion;
    }

    private String resolveVersion() {
        String pomVersion = getPomVersion();
        if (pomVersion != null && !pomVersion.isBlank()) {
            return pomVersion;
        }

        return "unknown";
    }

    private String normalizeReleaseVersion(String baseVersion) {
        if (baseVersion == null || baseVersion.isBlank()) {
            return "latest";
        }

        String normalized = baseVersion.trim();
        normalized = normalized.replaceFirst("(?i)^v", "");
        normalized = normalized.replaceFirst("-SNAPSHOT$", "");
        if (normalized.isBlank()) {
            return "latest";
        }
        return normalized;
    }

    private String getPomVersion() {
        try (var inputStream = getClass().getResourceAsStream("/META-INF/maven/dev.kruhlmann/imgfloat/pom.properties")) {
            if (inputStream == null) {
                return null;
            }
            var properties = new java.util.Properties();
            properties.load(inputStream);
            String pomVersion = properties.getProperty("version");
            if (pomVersion != null && !pomVersion.isBlank()) {
                return pomVersion.trim();
            }
        } catch (IOException e) {
            LOG.warn("Unable to read version from pom.properties", e);
        }
        return null;
    }
}
