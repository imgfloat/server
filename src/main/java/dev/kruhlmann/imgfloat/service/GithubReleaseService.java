package dev.kruhlmann.imgfloat.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class GithubReleaseService {

    private static final Logger LOG = LoggerFactory.getLogger(GithubReleaseService.class);

    private final String githubClientOwner;
    private final String githubClientRepo;
    private final String githubClientVersion;

    public GithubReleaseService(
        @Value("${IMGFLOAT_GITHUB_CLIENT_OWNER:#{null}}") String githubClientOwner,
        @Value("${IMGFLOAT_GITHUB_CLIENT_REPO:#{null}}") String githubClientRepo,
        @Value("${IMGFLOAT_GITHUB_CLIENT_VERSION:#{null}}") String githubClientVersion
    ) {
        this.githubClientOwner = githubClientOwner;
        this.githubClientRepo = githubClientRepo;
        this.githubClientVersion = githubClientVersion;
    }

    public String getDownloadBaseUrl() {
        validateConfiguration();
        String releaseTag = getClientReleaseTag();
        return String.format(
            "https://github.com/%s/%s/releases/download/%s/",
            githubClientOwner.trim(),
            githubClientRepo.trim(),
            releaseTag
        );
    }

    public String getClientReleaseVersion() {
        validateConfiguration();
        return normalizeReleaseVersion(githubClientVersion);
    }

    private String getClientReleaseTag() {
        String normalized = getClientReleaseVersion();
        String normalizedVersion = normalized.startsWith("v") ? normalized.substring(1) : normalized;
        return "v" + normalizedVersion;
    }

    private void validateConfiguration() {
        if (
            !StringUtils.hasText(githubClientOwner) ||
            !StringUtils.hasText(githubClientRepo) ||
            !StringUtils.hasText(githubClientVersion)
        ) {
            LOG.error(
                "GitHub client download configuration is missing (owner={}, repo={}, version={})",
                githubClientOwner,
                githubClientRepo,
                githubClientVersion
            );
            throw new IllegalStateException("Missing GitHub client configuration for download links");
        }
    }

    private String normalizeReleaseVersion(String baseVersion) {
        String normalized = baseVersion.trim();
        normalized = normalized.replaceFirst("(?i)^v", "");
        normalized = normalized.replaceFirst("-SNAPSHOT$", "");
        if (normalized.isBlank()) {
            throw new IllegalStateException("Invalid client version: " + baseVersion);
        }
        return normalized;
    }
}
