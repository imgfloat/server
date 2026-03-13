package dev.kruhlmann.imgfloat.service;

import dev.kruhlmann.imgfloat.service.git.GitCommitInfo;
import dev.kruhlmann.imgfloat.service.git.GitCommitInfoSource;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class GitInfoService {

    private static final String FALLBACK_GIT_SHA = "unknown";

    private final String commitSha;
    private final String shortCommitSha;
    private final String commitUrlPrefix;

    public GitInfoService(
        @Value("${IMGFLOAT_COMMIT_URL_PREFIX:}") String commitUrlPrefix,
        List<GitCommitInfoSource> commitInfoSources
    ) {
        GitCommitInfo commitInfo = resolveCommitInfo(commitInfoSources);
        String full = commitInfo != null ? commitInfo.fullSha() : null;
        String abbreviated = commitInfo != null ? commitInfo.shortSha() : null;

        if (abbreviated == null && full != null) {
            abbreviated = abbreviate(full);
        } else if (full == null && abbreviated != null) {
            full = abbreviated;
        }

        this.commitSha = defaultValue(full);
        this.shortCommitSha = defaultValue(abbreviated);
        this.commitUrlPrefix = normalize(commitUrlPrefix);
    }

    public String getShortCommitSha() {
        return shortCommitSha;
    }

    public String getCommitUrl() {
        if (!shouldShowCommitChip()
            || commitSha == null
            || commitSha.isBlank()
            || FALLBACK_GIT_SHA.equalsIgnoreCase(commitSha)) {
            return null;
        }
        return commitUrlPrefix + commitSha;
    }

    public boolean shouldShowCommitChip() {
        return StringUtils.hasText(commitUrlPrefix);
    }

    private GitCommitInfo resolveCommitInfo(List<GitCommitInfoSource> commitInfoSources) {
        if (commitInfoSources == null || commitInfoSources.isEmpty()) {
            return null;
        }
        Optional<GitCommitInfo> resolved = commitInfoSources
            .stream()
            .filter(Objects::nonNull)
            .map(GitCommitInfoSource::loadCommitInfo)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .map(this::normalizeCommitInfo)
            .filter(Objects::nonNull)
            .findFirst();
        return resolved.orElse(null);
    }

    private String abbreviate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() > 7 ? value.substring(0, 7) : value;
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String defaultValue(String value) {
        if (value == null || value.isBlank()) {
            return FALLBACK_GIT_SHA;
        }
        return value;
    }

    private GitCommitInfo normalizeCommitInfo(GitCommitInfo commitInfo) {
        if (commitInfo == null) {
            return null;
        }
        String fullSha = normalize(commitInfo.fullSha());
        String shortSha = normalize(commitInfo.shortSha());
        if (fullSha == null && shortSha == null) {
            return null;
        }
        return new GitCommitInfo(fullSha, shortSha);
    }
}
