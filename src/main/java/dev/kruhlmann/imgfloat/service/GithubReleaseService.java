package dev.kruhlmann.imgfloat.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class GithubReleaseService {

    private static final Logger LOG = LoggerFactory.getLogger(GithubReleaseService.class);

    private final VersionService versionService;
    private final String githubOwner;
    private final String githubRepo;

    public GithubReleaseService(
        VersionService versionService,
        @Value("${IMGFLOAT_GITHUB_OWNER:#{null}}") String githubOwner,
        @Value("${IMGFLOAT_GITHUB_REPO:#{null}}") String githubRepo
    ) {
        this.versionService = versionService;
        this.githubOwner = githubOwner;
        this.githubRepo = githubRepo;
    }

    public String getDownloadBaseUrl() {
        validateConfiguration();
        String releaseTag = versionService.getReleaseTag();
        return String.format(
            "https://github.com/%s/%s/releases/download/%s/",
            githubOwner.trim(),
            githubRepo.trim(),
            releaseTag
        );
    }

    private void validateConfiguration() {
        if (!StringUtils.hasText(githubOwner) || !StringUtils.hasText(githubRepo)) {
            LOG.error("GitHub download configuration is missing (owner={}, repo={})", githubOwner, githubRepo);
            throw new IllegalStateException("Missing GitHub owner or repo configuration for download links");
        }
    }
}
